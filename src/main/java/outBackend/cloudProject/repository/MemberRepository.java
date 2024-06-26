package outBackend.cloudProject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import outBackend.cloudProject.domain.Member;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String email);
}
