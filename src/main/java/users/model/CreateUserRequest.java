package users.model;
/** Matches supplied Pydantic UserCreate. Deliberately no client-side field validation: tests may send invalid values. */
public final class CreateUserRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String role = "USER";
    private String status = "ACTIVE";
    /** Bean constructor used by Jackson. */
    public CreateUserRequest() {}
    public CreateUserRequest(String firstName, String lastName, String email) {
        this.firstName = firstName; this.lastName = lastName; this.email = email;
    }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
