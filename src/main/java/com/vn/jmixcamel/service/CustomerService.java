package com.vn.jmixcamel.service;

import com.vn.jmixcamel.entity.Customer;
import com.vn.jmixcamel.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findByNameAndPhone(String name, String phone) {
        return customerRepository.findByNameAndPhone(name, phone);
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findByName(String name) {
        return customerRepository.findByName(name);
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findByPhone(String phone) {
        return customerRepository.findByPhone(phone);
    }
}
