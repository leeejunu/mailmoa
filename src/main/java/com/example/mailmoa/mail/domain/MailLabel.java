package com.example.mailmoa.mail.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class MailLabel {

    @EmbeddedId
    private MailLabelId id;
}
