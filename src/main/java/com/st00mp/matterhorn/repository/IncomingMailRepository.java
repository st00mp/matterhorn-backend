package com.st00mp.matterhorn.repository;

import com.st00mp.matterhorn.entity.IncomingMail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomingMailRepository extends JpaRepository<IncomingMail, Long> {
}
