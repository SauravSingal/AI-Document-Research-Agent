package com.example.AiAgent.entity;


import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "test_data")
public class tstData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String type;       // EMAIL or IN_APP
    private String title;
    private String message;

}
