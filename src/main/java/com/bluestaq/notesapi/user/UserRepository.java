package com.bluestaq.notesapi.user;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Atomic $addToSet rather than read-modify-save: two concurrent team creations by the same user must not
    // let one overwrite the other's membership update.
    @Query("{ '_id': ?0 }")
    @Update("{ '$addToSet': { 'teamIds': ?1 } }")
    void addTeamMembership(String userId, String teamId);
}
