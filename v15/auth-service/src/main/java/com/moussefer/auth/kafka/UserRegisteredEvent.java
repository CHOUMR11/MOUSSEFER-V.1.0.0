package com.moussefer.auth.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {
    private String userId;
    private String email;
    private String phoneNumber;
    private String role;
}