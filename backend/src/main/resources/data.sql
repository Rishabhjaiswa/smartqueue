-- Insert Doctors
INSERT INTO doctors(name, specialization, is_available, avg_consultation_minutes, delay_minutes)
VALUES
    ('Dr. Sharma',  'GENERAL',     true, 8,  0),
    ('Dr. Mehta',   'CARDIOLOGY',  true, 12, 0),
    ('Dr. Kaur',    'PEDIATRICS',  true, 10, 0);

-- Insert Staff Users
INSERT INTO staff_users(username, password, role)
VALUES
    ('reception1', '$2a$10$hashedpw', 'RECEPTIONIST'),
    ('admin1',     '$2a$10$hashedpw', 'ADMIN');

-- Doctor login accounts (linked to doctor_id)
INSERT INTO staff_users(username, password, role, doctor_id)
VALUES
    ('dr_sharma', '$2a$10$hashedpw', 'DOCTOR', 1);