package org.springframework.data.rest.webmvc.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SequenceRepository extends JpaRepository<Sequence, String> {
	
}
