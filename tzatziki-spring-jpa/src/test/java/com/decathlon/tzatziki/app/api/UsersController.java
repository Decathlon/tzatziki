package com.decathlon.tzatziki.app.api;

import com.decathlon.tzatziki.app.dao.UserDataSpringRepository;
import com.decathlon.tzatziki.app.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.springframework.http.ResponseEntity.ok;

@RestController
public class UsersController {

    @Autowired
    private UserDataSpringRepository userDataSpringRepository;

    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUser(@PathVariable Integer id) {
        return userDataSpringRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> listUsers() {
        return ok(stream(userDataSpringRepository.findAll().spliterator(), false).collect(toList()));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Integer id) {
        userDataSpringRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
