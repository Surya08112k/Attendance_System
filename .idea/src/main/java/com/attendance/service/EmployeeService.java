package com.attendance.service;

import com.attendance.model.Employee;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class EmployeeService {

    // In-memory employee master list (replace with DB in production)
    private static final Map<String, Employee> EMPLOYEES = new LinkedHashMap<>();

    static {
        EMPLOYEES.put("EMP001", new Employee("EMP001", "Arjun Sharma",       "Engineering"));
        EMPLOYEES.put("EMP002", new Employee("EMP002", "Priya Nair",         "Human Resources"));
        EMPLOYEES.put("EMP003", new Employee("EMP003", "Rahul Mehta",        "Finance"));
        EMPLOYEES.put("EMP004", new Employee("EMP004", "Sneha Krishnan",     "Marketing"));
        EMPLOYEES.put("EMP005", new Employee("EMP005", "Vikram Iyer",        "Engineering"));
        EMPLOYEES.put("EMP006", new Employee("EMP006", "Anjali Desai",       "Sales"));
        EMPLOYEES.put("EMP007", new Employee("EMP007", "Karthik Reddy",      "Operations"));
        EMPLOYEES.put("EMP008", new Employee("EMP008", "Meera Pillai",       "Engineering"));
        EMPLOYEES.put("EMP009", new Employee("EMP009", "Suresh Babu",        "Finance"));
        EMPLOYEES.put("EMP010", new Employee("EMP010", "Divya Chandran",     "Human Resources"));
    }

    public Optional<Employee> findById(String employeeId) {
        return Optional.ofNullable(EMPLOYEES.get(employeeId));
    }

    public List<Employee> getAllEmployees() {
        return new ArrayList<>(EMPLOYEES.values());
    }

    public boolean exists(String employeeId) {
        return EMPLOYEES.containsKey(employeeId);
    }
}
