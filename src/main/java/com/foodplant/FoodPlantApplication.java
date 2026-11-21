package com.foodplant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class FoodPlantApplication {
    public static void main(String[] args) { SpringApplication.run(FoodPlantApplication.class, args); }
}
