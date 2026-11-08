package com.tounsiguide.identity.service;

import com.tounsiguide.identity.domain.Role;
import com.tounsiguide.identity.domain.User;
import com.tounsiguide.identity.dto.PageResponse;
import com.tounsiguide.identity.dto.UserResponse;
import com.tounsiguide.identity.dto.UserSummary;
import com.tounsiguide.identity.exception.ApiException;
import com.tounsiguide.identity.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(String userId) {
        return UserResponse.from(find(userId));
    }

    @Transactional(readOnly = true)
    public UserSummary getSummary(String userId) {
        return UserSummary.from(find(userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Pageable pageable) {
        Page<User> page = userRepository.findAll(pageable);
        return new PageResponse<>(page.getContent().stream().map(UserResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /**
     * Remplace les rôles d'un utilisateur. MEMBER est toujours conservé. Un administrateur ne peut pas
     * se retirer lui-même le rôle ADMIN (évite de se retrouver sans administrateur par erreur).
     * Les access tokens déjà émis gardent leurs anciens rôles jusqu'à leur expiration (15 min par défaut).
     */
    @Transactional
    public UserResponse updateRoles(String actorId, String targetId, Set<Role> requested) {
        User target = find(targetId);
        Set<Role> newRoles = EnumSet.of(Role.MEMBER);
        newRoles.addAll(requested);
        if (actorId.equals(targetId) && !newRoles.contains(Role.ADMIN)) {
            throw ApiException.badRequest("Un administrateur ne peut pas retirer son propre rôle ADMIN");
        }
        target.getRoles().clear();
        target.getRoles().addAll(newRoles);
        return UserResponse.from(userRepository.save(target));
    }

    private User find(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
    }
}
