package com.mentis.hrms.service;

import com.mentis.hrms.model.Employee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProfilePictureService {

    private static final Logger logger = LoggerFactory.getLogger(ProfilePictureService.class);

    /**
     * Absolute base directory for all uploads.
     * Matches application.properties → app.upload.base-path=C:/hrms/uploads
     * and DashboardController's resource handler  → /uploads/** → file:///C:/hrms/uploads/
     */
    @Value("${app.upload.base-path:C:/hrms/uploads}")
    private String basePath;

    /**
     * Sub-folder used for profile pictures. DB stores paths like:
     *   employee-profiles/EMP001_<uuid>.jpg
     * and the UI renders:
     *   /uploads/employee-profiles/EMP001_<uuid>.jpg
     */
    private static final String PROFILE_SUBDIR = "employee-profiles";

    @Autowired
    private EmployeeService employeeService;

    /* =========================================================
       UPLOAD
       ========================================================= */
    public String uploadProfilePicture(String employeeId, MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please select a file to upload");
        }

        // Validate MIME type
        String contentType = file.getContentType();
        if (contentType == null ||
                !(contentType.equalsIgnoreCase("image/jpeg") ||
                        contentType.equalsIgnoreCase("image/png") ||
                        contentType.equalsIgnoreCase("image/jpg")  ||
                        contentType.equalsIgnoreCase("image/gif")  ||
                        contentType.equalsIgnoreCase("image/webp"))) {
            throw new IllegalArgumentException("Only JPG, PNG, GIF, and WEBP images are allowed");
        }

        // Validate size (5 MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("File size must be less than 5 MB");
        }

        Employee employee = employeeService.getEmployeeByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        // Ensure absolute base + sub-folder exist
        Path profileDir = Paths.get(basePath, PROFILE_SUBDIR).toAbsolutePath().normalize();
        Files.createDirectories(profileDir);

        // Build safe unique filename
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "photo.jpg";
        String ext = ".jpg";
        int dot = originalFilename.lastIndexOf('.');
        if (dot >= 0) ext = originalFilename.substring(dot);
        String uniqueFilename = employeeId + "_" + UUID.randomUUID().toString().replace("-", "") + ext;

        Path targetFile = profileDir.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);

        // Delete old file (resolved against absolute base — Bug 2 fix)
        if (employee.getProfilePicture() != null && !employee.getProfilePicture().trim().isEmpty()) {
            try {
                Path oldFile = Paths.get(basePath).resolve(employee.getProfilePicture()).normalize();
                Files.deleteIfExists(oldFile);
                logger.info("Deleted old profile picture: {}", oldFile);
            } catch (Exception e) {
                logger.warn("Could not delete old profile picture ({}): {}",
                        employee.getProfilePicture(), e.getMessage());
            }
        }

        // Store path relative to /uploads — e.g. "employee-profiles/EMP001_abc.jpg"
        String relativePath = PROFILE_SUBDIR + "/" + uniqueFilename;
        employee.setProfilePicture(relativePath);
        employee.setUpdatedDate(LocalDateTime.now());

        // Bug 3 fix: use updateEmployee (existing record) instead of saveEmployee (create path)
        employeeService.updateEmployee(employee);

        logger.info("Profile picture uploaded for employee {}: {}", employeeId, relativePath);
        return relativePath;
    }

    /* =========================================================
       DELETE
       ========================================================= */
    public boolean deleteProfilePicture(String employeeId) throws IOException {

        Employee employee = employeeService.getEmployeeByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        if (employee.getProfilePicture() == null || employee.getProfilePicture().trim().isEmpty()) {
            return false;   // nothing to delete
        }

        // Delete the file from disk (absolute resolution)
        try {
            Path filePath = Paths.get(basePath).resolve(employee.getProfilePicture()).normalize();
            Files.deleteIfExists(filePath);
            logger.info("Deleted profile picture file: {}", filePath);
        } catch (Exception e) {
            logger.warn("Could not delete profile picture file ({}): {}",
                    employee.getProfilePicture(), e.getMessage());
        }

        // Clear DB field
        employee.setProfilePicture(null);
        employee.setUpdatedDate(LocalDateTime.now());
        employeeService.updateEmployee(employee);

        logger.info("Profile picture cleared for employee {}", employeeId);
        return true;
    }

    /* =========================================================
       READ (used by GET /candidate/profile-picture/{id} if you need it)
       ========================================================= */
    public byte[] getProfilePicture(String employeeId) throws IOException {

        Employee employee = employeeService.getEmployeeByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));

        if (employee.getProfilePicture() == null || employee.getProfilePicture().trim().isEmpty()) {
            return null;
        }

        Path filePath = Paths.get(basePath).resolve(employee.getProfilePicture()).normalize();
        if (!Files.exists(filePath)) {
            return null;
        }
        return Files.readAllBytes(filePath);
    }
}