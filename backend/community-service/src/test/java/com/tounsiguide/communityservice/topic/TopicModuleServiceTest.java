package com.tounsiguide.communityservice.topic;

import com.tounsiguide.communityservice.common.error.ConflictException;
import com.tounsiguide.communityservice.common.error.InvalidParameterException;
import com.tounsiguide.communityservice.common.security.CurrentUser;
import com.tounsiguide.communityservice.common.security.ForbiddenException;
import com.tounsiguide.communityservice.post.PostRepository;
import com.tounsiguide.communityservice.post.PublicationStatus;
import com.tounsiguide.communityservice.topic.dto.ModuleDtos.ModuleResponse;
import com.tounsiguide.communityservice.topic.dto.ModuleDtos.ProposeModuleRequest;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
class TopicModuleServiceTest {

    private static final CurrentUser MEMBER = new CurrentUser("user-1", "Sarra", Set.of("MEMBER"));
    private static final CurrentUser ADMIN = new CurrentUser("admin-1", "Admin", Set.of("MEMBER", "ADMIN"));

    @Mock
    private TopicModuleRepository modules;
    @Mock
    private PostRepository posts;

    private TopicModuleService service;

    @BeforeEach
    void setUp() {
        service = new TopicModuleService(modules, posts);
        lenient().when(modules.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static TopicModule module(long id, String name, ModuleStatus status) {
        TopicModule module = new TopicModule(TopicModuleService.slugify(name), name, "Description", null, "user-1", "Sarra");
        setField(module, "id", id);
        setField(module, "status", status);
        return module;
    }

    @Test
    void slugsAreLowercaseAsciiWithDashes() {
        assertThat(TopicModuleService.slugify("Cafés & Thé à Tunis")).isEqualTo("cafes-the-a-tunis");
        assertThat(TopicModuleService.slugify("  Plages!! ")).isEqualTo("plages");
        assertThat(TopicModuleService.slugify("€€€")).isEmpty();
    }

    @Test
    void aMemberProposesAModuleThatStartsProposed() {
        ModuleResponse response = service.propose(new ProposeModuleRequest("  Cafés   de Tunis ", " Où travailler ", "☕"), MEMBER);

        assertThat(response.status()).isEqualTo(ModuleStatus.PROPOSED);
        assertThat(response.name()).isEqualTo("Cafés de Tunis");
        assertThat(response.slug()).isEqualTo("cafes-de-tunis");
        assertThat(response.description()).isEqualTo("Où travailler");
        assertThat(response.icon()).isEqualTo("☕");
        assertThat(response.proposedByName()).isEqualTo("Sarra");
    }

    @Test
    void aDuplicateNameOrSlugAmongNonRejectedModulesIs409() {
        when(modules.existsByNameIgnoreCaseAndStatusNot("Plages", ModuleStatus.REJECTED)).thenReturn(false);
        when(modules.existsBySlugAndStatusNot("plages", ModuleStatus.REJECTED)).thenReturn(true);

        assertThatThrownBy(() -> service.propose(new ProposeModuleRequest("Plages", "x", null), MEMBER))
                .isInstanceOf(ConflictException.class);
        verify(modules, never()).save(any());
    }

    @Test
    void aNameWithoutLettersIsRefused() {
        assertThatThrownBy(() -> service.propose(new ProposeModuleRequest("€€€", "x", null), MEMBER))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void thePublicListHasApprovedModulesWithTheirPostCounts() {
        when(modules.findAllByStatusOrderByNameAsc(ModuleStatus.APPROVED))
                .thenReturn(List.of(module(1, "Cafés", ModuleStatus.APPROVED), module(2, "Plages", ModuleStatus.APPROVED)));
        List<Object[]> counts = List.<Object[]>of(new Object[]{1L, 4L});
        when(posts.countPublishedByModule(PublicationStatus.PUBLISHED)).thenReturn(counts);

        List<ModuleResponse> list = service.approved();

        assertThat(list).extracting(ModuleResponse::postCount).containsExactly(4L, 0L);
    }

    @Test
    void aModulePageIsForApprovedModulesOnlyExceptForAdministrators() {
        when(modules.findFirstBySlugAndStatus("plages", ModuleStatus.APPROVED)).thenReturn(Optional.empty());
        when(modules.findFirstBySlugOrderByIdDesc("plages")).thenReturn(Optional.of(module(2, "Plages", ModuleStatus.PROPOSED)));

        assertThatThrownBy(() -> service.bySlug("plages", false)).isInstanceOf(EntityNotFoundException.class);
        assertThat(service.bySlug("plages", true).status()).isEqualTo(ModuleStatus.PROPOSED);
    }

    @Test
    void onlyApprovedModulesCanBeChosenOnAPost() {
        when(modules.findById(1L)).thenReturn(Optional.of(module(1, "Cafés", ModuleStatus.APPROVED)));
        when(modules.findById(2L)).thenReturn(Optional.of(module(2, "Plages", ModuleStatus.PROPOSED)));
        when(modules.findById(3L)).thenReturn(Optional.empty());

        service.requireSelectable(null);
        service.requireSelectable(1L);
        assertThatThrownBy(() -> service.requireSelectable(2L)).isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> service.requireSelectable(3L)).isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void decisionsAreAdminOnly() {
        assertThatThrownBy(() -> service.approve(1L, MEMBER)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.reject(1L, "x", MEMBER)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.archive(1L, null, MEMBER)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.byStatus(ModuleStatus.PROPOSED, 0, 20, MEMBER)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(modules);
    }

    @Test
    void theProposedQueueIsOldestFirst() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(modules.findAllByStatus(eq(ModuleStatus.PROPOSED), pageable.capture())).thenReturn(new PageImpl<>(List.of()));

        service.byStatus(ModuleStatus.PROPOSED, 0, 20, ADMIN);

        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isAscending()).isTrue();
    }

    @Test
    void approvingRecordsTheAdminAndRefusesTwice() {
        TopicModule proposed = module(1, "Cafés", ModuleStatus.PROPOSED);
        when(modules.findById(1L)).thenReturn(Optional.of(proposed));

        ModuleResponse response = service.approve(1L, ADMIN);

        assertThat(response.status()).isEqualTo(ModuleStatus.APPROVED);
        assertThat(response.decidedByName()).isEqualTo("Admin");
        assertThatThrownBy(() -> service.approve(1L, ADMIN)).isInstanceOf(ConflictException.class);
    }

    @Test
    void approvingIsRefusedWhenAnotherApprovedModuleHasTheSlug() {
        when(modules.findById(1L)).thenReturn(Optional.of(module(1, "Cafés", ModuleStatus.PROPOSED)));
        when(modules.findFirstBySlugAndStatus("cafes", ModuleStatus.APPROVED)).thenReturn(Optional.of(module(9, "Cafés", ModuleStatus.APPROVED)));

        assertThatThrownBy(() -> service.approve(1L, ADMIN)).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectingNeedsAReasonAndOnlyAppliesToProposedModules() {
        TopicModule proposed = module(1, "Cafés", ModuleStatus.PROPOSED);
        when(modules.findById(1L)).thenReturn(Optional.of(proposed));

        assertThatThrownBy(() -> service.reject(1L, " ", ADMIN)).isInstanceOf(InvalidParameterException.class);
        ModuleResponse rejected = service.reject(1L, " Doublon de « Cafés » ", ADMIN);
        assertThat(rejected.status()).isEqualTo(ModuleStatus.REJECTED);
        assertThat(rejected.decisionReason()).isEqualTo("Doublon de « Cafés »");
        assertThatThrownBy(() -> service.reject(1L, "x", ADMIN)).isInstanceOf(ConflictException.class);
    }

    @Test
    void archivingAppliesToApprovedModulesAndCanBeUndoneByApproving() {
        TopicModule approved = module(1, "Cafés", ModuleStatus.APPROVED);
        when(modules.findById(1L)).thenReturn(Optional.of(approved));

        assertThat(service.archive(1L, null, ADMIN).status()).isEqualTo(ModuleStatus.ARCHIVED);
        assertThatThrownBy(() -> service.archive(1L, null, ADMIN)).isInstanceOf(ConflictException.class);
        assertThat(service.approve(1L, ADMIN).status()).isEqualTo(ModuleStatus.APPROVED);
    }
}
