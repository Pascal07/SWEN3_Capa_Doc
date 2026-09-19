package at.capadocapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CapaDocApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CapaDocApiApplication.class, args);
        for (int i = 0; i < 10; i++) {
        System.out.println("Hello World");
        }
    }

}
