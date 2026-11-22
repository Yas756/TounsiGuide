package com.tounsiguide.communityservice.topic;

import com.tounsiguide.communityservice.common.PageResponse;
import com.tounsiguide.communityservice.common.error.ConflictException;
import com.tounsiguide.communityservice.common.error.InvalidParameterException;
import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.common.security.ForbiddenException;
import com.tounsiguide.communityservice.post.PostRepository;
import com.tounsiguide.communityservice.post.PublicationStatus;
import com.tounsiguide.communityservice.topic.dto.ModuleDtos.ModuleResponse;
import com.tounsiguide.communityservice.topic.dto.ModuleDtos.ProposeModuleRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Topic modules (FR-05, S3-US09, S3-B08): members propose, administrators approve, reject (with a reason) or archive.
 * Only APPROVED modules are listed, have a page and can be chosen on a post.
 */
@Service
public class TopicModuleService {

    public static final int MAX_PAGE_SIZE = 50;
    static final int MAX_SLUG_LENGTH = 70;

    private final TopicModuleRepository modules;
    private final PostRepository posts;

    public TopicModuleService(TopicModuleRepository modules, PostRepository posts) {
        this.modules = modules;
        this.posts = posts;
    }

    // ------------------------------------------------------------------ public reads

    /** Approved modules by name, each with its number of published posts (one grouped query). */
    @Transactional(readOnly = true)
    public List<ModuleResponse> approved() {
        Map<Long, Long> counts = postCounts();
        return modules.findAllByStatusOrderByNameAsc(ModuleStatus.APPROVED).stream()
                .map(module -> ModuleResponse.from(module, counts.getOrDefault(module.getId(), 0L)))
                .toList();
    }

    /** An approved module by slug; an administrator also gets proposed, rejected or archived ones. 404 otherwise. */
    @Transactional(readOnly = true)
    public ModuleResponse bySlug(String slug, boolean admin) {
        TopicModule module = modules.findFirstBySlugAndStatus(slug, ModuleStatus.APPROVED)
                .or(() -> admin ? modules.findFirstBySlugOrderByIdDesc(slug) : java.util.Optional.empty())
                .orElseThrow(() -> new EntityNotFoundException("Module introuvable"));
        return ModuleResponse.from(module, postCounts().getOrDefault(module.getId(), 0L));
    }

    /** The approved module with this slug (feed filter); 404 otherwise. */
    @Transactional(readOnly = true)
    public TopicModule approvedBySlug(String slug) {
        return modules.findFirstBySlugAndStatus(slug, ModuleStatus.APPROVED)
                .orElseThrow(() -> new EntityNotFoundException("Module introuvable"));
    }

    /** Approved modules among these ids, keyed by id (posts only show approved modules). */
    @Transactional(readOnly = true)
    public Map<Long, TopicModule> approvedByIds(Collection<Long> ids) {
        List<Long> wanted = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return modules.findAllByIdIn(wanted).stream().filter(TopicModule::isApproved)
                .collect(Collectors.toMap(TopicModule::getId, Function.identity()));
    }

    /**
     * Checks a module chosen on a post: null is fine (no module); otherwise it must exist and be APPROVED.
     *
     * @throws InvalidParameterException 400 VALIDATION_ERROR with {@code details.moduleId}
     */
    @Transactional(readOnly = true)
    public void requireSelectable(Long moduleId) {
        if (moduleId == null) {
            return;
        }
        boolean ok = modules.findById(moduleId).map(TopicModule::isApproved).orElse(false);
        if (!ok) {
            throw new InvalidParameterException("moduleId", "Ce module n'existe pas ou n'est pas approuvé");
        }
    }

    // ------------------------------------------------------------------ members

    /**
     * @throws ConflictException 409 when a module with the same name or slug exists and was not rejected
     */
    @Transactional
    public ModuleResponse propose(ProposeModuleRequest body, CurrentUser user) {
        String name = body.name().trim().replaceAll("\\s+", " ");
        String slug = slugify(name);
        if (slug.isEmpty()) {
            throw new InvalidParameterException("name", "Le nom doit contenir des lettres ou des chiffres");
        }
        if (modules.existsByNameIgnoreCaseAndStatusNot(name, ModuleStatus.REJECTED)
                || modules.existsBySlugAndStatusNot(slug, ModuleStatus.REJECTED)) {
            throw new ConflictException("Un module porte déjà ce nom (proposé ou approuvé)");
        }
        String icon = body.icon() == null || body.icon().isBlank() ? null : body.icon().trim();
        TopicModule saved = modules.save(new TopicModule(slug, name, body.description().trim(), icon, user.id(),
                user.displayName()));
        return ModuleResponse.from(saved, 0);
    }

    @Transactional(readOnly = true)
    public List<ModuleResponse> mine(CurrentUser user) {
        return modules.findAllByProposedByOrderByCreatedAtDesc(user.id()).stream()
                .map(module -> ModuleResponse.from(module, 0)).toList();
    }

    // ------------------------------------------------------------------ administrators

    /** Modules with a status, paged; PROPOSED oldest first (first come, first served), others newest first. */
    @Transactional(readOnly = true)
    public PageResponse<ModuleResponse> byStatus(ModuleStatus status, int page, int size, CurrentUser user) {
        requireAdmin(user);
        Sort sort = status == ModuleStatus.PROPOSED
                ? Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        PageRequest request = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)), sort);
        Map<Long, Long> counts = status == ModuleStatus.APPROVED ? postCounts() : Map.of();
        return PageResponse.of(modules.findAllByStatus(status, request),
                module -> ModuleResponse.from(module, counts.getOrDefault(module.getId(), 0L)));
    }

    /** PROPOSED or ARCHIVED → APPROVED. 409 otherwise, or when another module already uses the slug. */
    @Transactional
    public ModuleResponse approve(Long id, CurrentUser user) {
        requireAdmin(user);
        TopicModule module = find(id);
        if (module.getStatus() != ModuleStatus.PROPOSED && module.getStatus() != ModuleStatus.ARCHIVED) {
            throw new ConflictException("Seul un module proposé ou archivé peut être approuvé");
        }
        boolean slugTaken = modules.findFirstBySlugAndStatus(module.getSlug(), ModuleStatus.APPROVED)
                .filter(other -> !other.getId().equals(module.getId())).isPresent();
        if (slugTaken) {
            throw new ConflictException("Un module approuvé porte déjà ce nom");
        }
        module.decide(ModuleStatus.APPROVED, user.id(), user.displayName(), null);
        return ModuleResponse.from(modules.save(module), 0);
    }

    /** PROPOSED → REJECTED with a reason the proposer reads. */
    @Transactional
    public ModuleResponse reject(Long id, String reason, CurrentUser user) {
        requireAdmin(user);
        String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (cleanReason == null) {
            throw new InvalidParameterException("reason", "Le motif du refus est obligatoire");
        }
        TopicModule module = find(id);
        if (module.getStatus() != ModuleStatus.PROPOSED) {
            throw new ConflictException("Seul un module proposé peut être refusé");
        }
        module.decide(ModuleStatus.REJECTED, user.id(), user.displayName(), cleanReason);
        return ModuleResponse.from(modules.save(module), 0);
    }

    /** APPROVED → ARCHIVED: no longer listed or selectable; existing posts keep it in the database. */
    @Transactional
    public ModuleResponse archive(Long id, String reason, CurrentUser user) {
        requireAdmin(user);
        TopicModule module = find(id);
        if (module.getStatus() != ModuleStatus.APPROVED) {
            throw new ConflictException("Seul un module approuvé peut être archivé");
        }
        module.decide(ModuleStatus.ARCHIVED, user.id(), user.displayName(),
                reason == null || reason.isBlank() ? null : reason.trim());
        return ModuleResponse.from(modules.save(module), 0);
    }

    // ------------------------------------------------------------------ helpers

    /** "Cafés & Thé à Tunis" → "cafes-the-a-tunis". */
    public static String slugify(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.length() <= MAX_SLUG_LENGTH ? slug : slug.substring(0, MAX_SLUG_LENGTH).replaceAll("-+$", "");
    }

    private Map<Long, Long> postCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : posts.countPublishedByModule(PublicationStatus.PUBLISHED)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private TopicModule find(Long id) {
        return modules.findById(id).orElseThrow(() -> new EntityNotFoundException("Module introuvable"));
    }

    private static void requireAdmin(CurrentUser user) {
        if (!user.isAdmin()) {
            throw new ForbiddenException("Action réservée aux administrateurs");
        }
    }
}
