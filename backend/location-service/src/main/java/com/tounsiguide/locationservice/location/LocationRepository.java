package com.tounsiguide.locationservice.location;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    /** Candidates whose name, alias or city contains the normalized term. The matcher ranks them afterwards. */
    @Query("""
            select distinct l from Location l
            left join l.aliases a
            where l.normalizedName like concat('%', :term, '%')
               or a.normalizedAlias like concat('%', :term, '%')
               or l.normalizedCity like concat('%', :term, '%')
            """)
    List<Location> findCandidates(@Param("term") String normalizedTerm, Pageable pageable);

    @EntityGraph(attributePaths = "aliases")
    List<Location> findByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = "aliases")
    Optional<Location> findWithAliasesById(Long id);

    boolean existsByNormalizedNameAndNormalizedCity(String normalizedName, String normalizedCity);

    boolean existsByNormalizedNameAndNormalizedCityIsNull(String normalizedName);

    Optional<Location> findFirstByExternalId(String externalId);

    List<Location> findByNormalizedName(String normalizedName);

    List<Location> findAllByOrderByNameAsc(Pageable pageable);
}
