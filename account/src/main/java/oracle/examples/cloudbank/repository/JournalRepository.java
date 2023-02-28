package oracle.examples.cloudbank.repository;

import oracle.examples.cloudbank.model.Journal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JournalRepository extends JpaRepository<Journal, Long> {
    List<Journal> findJournalByLraId(String lraId);
}
