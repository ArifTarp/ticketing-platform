package com.demo.ticketing.event.infra;

import com.demo.ticketing.event.domain.Event;
import com.demo.ticketing.event.domain.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Catalog browse query for the event-list screen. Every filter is optional (null = not applied);
     * {@code status} is always supplied by the caller so unpublished events can never leak.
     * The venue and price tiers are fetch-joined because every card needs the venue name and the
     * lowest tier price.
     *
     * <p>Two bind-type quirks are handled by the caller rather than by casts in the query:
     * <ul>
     *   <li>{@code city}/{@code titlePattern} arrive already lower-cased (pattern already wrapped in
     *       {@code %}). Applying {@code lower()} to a nullable parameter makes Postgres guess the
     *       bind type and fail with {@code function lower(bytea) does not exist}.</li>
     *   <li>{@code from}/{@code to} are never null — the caller substitutes open bounds. A bare
     *       {@code :param is null} test on a timestamp parameter fails with
     *       {@code could not determine data type of parameter}.</li>
     * </ul>
     */
    @Query("""
            select distinct e from Event e
            join fetch e.venue v
            left join fetch e.seatCategories
            where e.status = :status
              and (:city is null or lower(v.city) = :city)
              and (:titlePattern is null or lower(e.title) like :titlePattern)
              and e.startsAt >= :from
              and e.startsAt <= :to
            order by e.startsAt asc
            """)
    List<Event> findForBrowse(@Param("status") EventStatus status,
                              @Param("city") String city,
                              @Param("titlePattern") String titlePattern,
                              @Param("from") Instant from,
                              @Param("to") Instant to);

    @Query("""
            select e from Event e
            join fetch e.venue
            left join fetch e.seatCategories
            where e.id = :id
            """)
    Optional<Event> findDetailById(@Param("id") Long id);
}
