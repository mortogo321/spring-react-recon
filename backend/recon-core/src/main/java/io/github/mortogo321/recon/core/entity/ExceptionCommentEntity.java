package io.github.mortogo321.recon.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Investigation trail on a break. Append-only: comments are never edited or deleted. */
@Entity
@Table(name = "exception_comment", indexes = @Index(name = "ix_comment_exception", columnList = "exception_id"))
public class ExceptionCommentEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exception_id", nullable = false)
    private ReconExceptionEntity exception;

    @Column(nullable = false, length = 64)
    private String author;

    @Column(nullable = false, length = 2000)
    private String body;

    protected ExceptionCommentEntity() {
        // for JPA
    }

    public ExceptionCommentEntity(String author, String body) {
        this.author = author;
        this.body = body;
    }

    void attachTo(ReconExceptionEntity exception) {
        this.exception = exception;
    }

    public Long getId() {
        return id;
    }

    public String getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }
}
