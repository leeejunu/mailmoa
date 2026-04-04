package com.example.mailmoa.mail.domain;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@NoArgsConstructor
@EqualsAndHashCode
public class MailLabelId implements Serializable {
    private Long mailId;
    private Long labelId;
}
