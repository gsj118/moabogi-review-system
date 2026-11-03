package org.mnu.domain;
public class SignupForm {
    private String username = "", password = "", name = "", email = "";
    public String getUsername() { return username; } public void setUsername(String v) { username = v == null ? "" : v.trim(); }
    public String getPassword() { return password; } public void setPassword(String v) { password = v == null ? "" : v; }
    public String getName() { return name; } public void setName(String v) { name = v == null ? "" : v.trim(); }
    public String getEmail() { return email; } public void setEmail(String v) { email = v == null ? "" : v.trim(); }
}
