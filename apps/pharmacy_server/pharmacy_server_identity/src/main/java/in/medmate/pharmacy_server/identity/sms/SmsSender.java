package in.medmate.pharmacy_server.identity.sms;

import in.medmate.pharmacy_server.identity.otp.OtpPurpose;

public interface SmsSender {

    void send(String phone, String code, OtpPurpose purpose);
}
