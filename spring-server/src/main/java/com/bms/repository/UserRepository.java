
package com.bms.repository;

import com.bms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUsernameIgnoreCaseOrEmailIgnoreCase(
            String username, String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select u from User u where u.id = :id")
        Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Query("select u from User u where u.role = :role "
            + "and (lower(u.username) like lower(concat('%', :query, '%')) "
            + "or lower(u.fullName) like lower(concat('%', :query, '%')) "
            + "or lower(u.email) like lower(concat('%', :query, '%'))) "
            + "order by u.id desc")
    List<User> searchCustomers(@Param("role") User.Role role,
            @Param("query") String query);

    List<User> findTop100ByRoleOrderByIdDesc(User.Role role);
}
