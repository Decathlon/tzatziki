package com.decathlon.tzatziki.app.user;

import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(value = "users")
@Getter
@Setter
public class User {

    @Id
    public String id;
    public String firstName;
    public String lastName;
    private Instant birthDate;
    private OffsetDateTime creationDate;
    private OffsetDateTime lastUpdateDate;
}
