package com.vn.jmixcamel.repository;

import com.vn.jmixcamel.entity.Customer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CustomerRepository {

    @PersistenceContext
    private EntityManager em;

    public Optional<Customer> findByNameAndPhone(String name, String phone) {
        TypedQuery<Customer> q = em.createQuery(
                "select c from Customer c " +
                        "where c.name = :name and c.phone = :phone",
                Customer.class
        );
        q.setParameter("name", name);
        q.setParameter("phone", phone);
        q.setMaxResults(1);
        List<Customer> list = q.getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<Customer> findByName(String name) {
        TypedQuery<Customer> q = em.createQuery(
                "select c from Customer c where c.name = :name",
                Customer.class
        );
        q.setParameter("name", name);
        q.setMaxResults(1);
        List<Customer> list = q.getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<Customer> findByPhone(String phone) {
        TypedQuery<Customer> q = em.createQuery(
                "select c from Customer c where c.phone = :phone",
                Customer.class
        );
        q.setParameter("phone", phone);
        q.setMaxResults(1);
        List<Customer> list = q.getResultList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
