package com.tranqui.identidad.web.dto;

public class SetNewPasswordDto {
    private String newPassword;

    public SetNewPasswordDto() {}

    public SetNewPasswordDto(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
