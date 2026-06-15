package com.livestream.dao;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.model.User;
import io.dropwizard.hibernate.AbstractDAO;
import java.util.Optional;
import org.hibernate.SessionFactory;

@Singleton
public class UserDAO extends AbstractDAO<User> {

    @Inject
    public UserDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(get(id));
    }

    public Optional<User> findByUsername(String username) {
        return currentSession()
                .createQuery("FROM User u WHERE u.username = :username", User.class)
                .setParameter("username", username)
                .uniqueResultOptional();
    }
}
