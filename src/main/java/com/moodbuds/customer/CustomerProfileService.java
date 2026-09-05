package com.moodbuds.customer;

import static com.moodbuds.customer.api.CustomerDtos.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.moodbuds.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerProfileService {
    private static final int MAX_ACTIVE_ADDRESSES = 10;

    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final PasswordEncoder passwordEncoder;
    private final CustomerAuthService auth;

    public CustomerProfileService(JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc,
                                  PasswordEncoder passwordEncoder, CustomerAuthService auth) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.passwordEncoder = passwordEncoder;
        this.auth = auth;
    }

    public CustomerProfile profile(long customerId) {
        return CustomerAuthService.profile(activeCustomer(customerId));
    }

    @Transactional
    public CustomerProfile update(long customerId, UpdateProfileRequest request) {
        var current = activeCustomer(customerId);
        if (request.firstName() == null && request.lastName() == null && request.mobile() == null
                && request.dateOfBirth() == null && request.gender() == null) {
            throw ApiException.badRequest("EMPTY_REQUEST", "At least one profile field is required");
        }
        String mobile = request.mobile() == null ? current.mobile() : request.mobile().trim();
        boolean mobileChanged = request.mobile() != null && !java.util.Objects.equals(current.mobile(), mobile);
        jdbc.sql("""
                UPDATE users SET first_name=:firstName,last_name=:lastName,mobile=:mobile,
                    mobile_verified=CASE WHEN :mobileChanged THEN 0 ELSE mobile_verified END,
                    date_of_birth=:dateOfBirth,gender=:gender,updated_at=UTC_TIMESTAMP()
                WHERE id=:id
                """).param("firstName", request.firstName() == null ? current.firstName() : request.firstName().trim())
                .param("lastName", request.lastName() == null ? current.lastName() : request.lastName().trim())
                .param("mobile", mobile, java.sql.Types.VARCHAR).param("mobileChanged", mobileChanged)
                .param("dateOfBirth", request.dateOfBirth() == null ? current.dateOfBirth() : request.dateOfBirth(), java.sql.Types.DATE)
                .param("gender", request.gender() == null ? current.gender().name() : request.gender().name())
                .param("id", customerId).update();
        return profile(customerId);
    }

    @Transactional
    public void changePassword(long customerId, String currentSessionId, ChangePasswordRequest request) {
        var customer = activeCustomer(customerId);
        if (customer.passwordHash() == null || !passwordEncoder.matches(request.currentPassword(), customer.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CURRENT_PASSWORD", "The current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), customer.passwordHash())) {
            throw ApiException.badRequest("PASSWORD_UNCHANGED", "The new password must be different from the current password");
        }
        jdbc.sql("""
                UPDATE users SET password_hash=:hash,failed_login_attempts=0,locked_until=NULL,updated_at=UTC_TIMESTAMP()
                WHERE id=:id
                """).param("hash", passwordEncoder.encode(request.newPassword())).param("id", customerId).update();
        jdbc.sql("""
                UPDATE customer_auth_sessions SET revoked_at=UTC_TIMESTAMP()
                WHERE user_id=:userId AND id<>:currentSessionId AND revoked_at IS NULL
                """).param("userId", customerId).param("currentSessionId", currentSessionId).update();
    }

    @Transactional
    public void deactivate(long customerId) {
        activeCustomer(customerId);
        jdbc.sql("UPDATE users SET is_active=0,updated_at=UTC_TIMESTAMP() WHERE id=:id")
                .param("id", customerId).update();
        jdbc.sql("""
                UPDATE customer_auth_sessions SET revoked_at=UTC_TIMESTAMP()
                WHERE user_id=:id AND revoked_at IS NULL
                """).param("id", customerId).update();
    }

    public List<AddressResponse> addresses(long customerId) {
        activeCustomer(customerId);
        return jdbc.sql("""
                SELECT id,full_name,mobile,address_line1,address_line2,city,state,country,pincode,
                       address_type,is_default,created_at,updated_at
                FROM user_addresses WHERE user_id=:userId AND is_active=1
                ORDER BY is_default DESC,id DESC
                """).param("userId", customerId).query((rs, rowNum) -> address(rs)).list();
    }

    public AddressResponse address(long customerId, long addressId) {
        activeCustomer(customerId);
        return jdbc.sql("""
                SELECT id,full_name,mobile,address_line1,address_line2,city,state,country,pincode,
                       address_type,is_default,created_at,updated_at
                FROM user_addresses WHERE id=:id AND user_id=:userId AND is_active=1
                """).param("id", addressId).param("userId", customerId)
                .query((rs, rowNum) -> address(rs)).optional().orElseThrow(() -> ApiException.notFound("Address"));
    }

    @Transactional
    public AddressResponse createAddress(long customerId, AddressRequest request) {
        activeCustomer(customerId);
        int count = jdbc.sql("SELECT COUNT(*) FROM user_addresses WHERE user_id=:id AND is_active=1")
                .param("id", customerId).query(Integer.class).single();
        if (count >= MAX_ACTIVE_ADDRESSES) {
            throw new ApiException(HttpStatus.CONFLICT, "ADDRESS_LIMIT_REACHED", "A customer can have at most 10 active addresses");
        }
        boolean makeDefault = request.defaultAddress() || count == 0;
        if (makeDefault) clearDefaults(customerId);
        var params = addressParams(customerId, request).addValue("isDefault", makeDefault);
        var keys = new GeneratedKeyHolder();
        namedJdbc.update("""
                INSERT INTO user_addresses(user_id,full_name,mobile,address_line1,address_line2,city,state,country,
                                           pincode,address_type,is_default,is_active,created_at,updated_at)
                VALUES(:userId,:fullName,:mobile,:line1,:line2,:city,:state,:country,
                       :pincode,:type,:isDefault,1,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                """, params, keys, new String[]{"id"});
        return address(customerId, keys.getKey().longValue());
    }

    @Transactional
    public AddressResponse updateAddress(long customerId, long addressId, AddressRequest request) {
        address(customerId, addressId);
        if (request.defaultAddress()) clearDefaults(customerId);
        var params = addressParams(customerId, request).addValue("id", addressId)
                .addValue("isDefault", request.defaultAddress());
        namedJdbc.update("""
                UPDATE user_addresses SET full_name=:fullName,mobile=:mobile,address_line1=:line1,
                    address_line2=:line2,city=:city,state=:state,country=:country,pincode=:pincode,
                    address_type=:type,is_default=:isDefault,updated_at=UTC_TIMESTAMP()
                WHERE id=:id AND user_id=:userId AND is_active=1
                """, params);
        ensureDefault(customerId);
        return address(customerId, addressId);
    }

    @Transactional
    public AddressResponse makeDefault(long customerId, long addressId) {
        address(customerId, addressId);
        clearDefaults(customerId);
        jdbc.sql("UPDATE user_addresses SET is_default=1,updated_at=UTC_TIMESTAMP() WHERE id=:id AND user_id=:userId")
                .param("id", addressId).param("userId", customerId).update();
        return address(customerId, addressId);
    }

    @Transactional
    public void deleteAddress(long customerId, long addressId) {
        var existing = address(customerId, addressId);
        jdbc.sql("UPDATE user_addresses SET is_active=0,is_default=0,updated_at=UTC_TIMESTAMP() WHERE id=:id AND user_id=:userId")
                .param("id", addressId).param("userId", customerId).update();
        if (existing.defaultAddress()) ensureDefault(customerId);
    }

    CustomerAuthService.UserRow activeCustomer(long customerId) {
        var customer = auth.load(customerId);
        if (!customer.active()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_DISABLED", "This customer account is disabled");
        }
        return customer;
    }

    public void requireActive(long customerId) {
        activeCustomer(customerId);
    }

    private void clearDefaults(long customerId) {
        jdbc.sql("UPDATE user_addresses SET is_default=0 WHERE user_id=:id AND is_active=1")
                .param("id", customerId).update();
    }

    private void ensureDefault(long customerId) {
        int defaults = jdbc.sql("SELECT COUNT(*) FROM user_addresses WHERE user_id=:id AND is_active=1 AND is_default=1")
                .param("id", customerId).query(Integer.class).single();
        if (defaults == 0) {
            jdbc.sql("""
                    UPDATE user_addresses SET is_default=1,updated_at=UTC_TIMESTAMP()
                    WHERE id=(SELECT selected.id FROM (SELECT id FROM user_addresses
                              WHERE user_id=:id AND is_active=1 ORDER BY id DESC LIMIT 1) selected)
                    """).param("id", customerId).update();
        }
    }

    private MapSqlParameterSource addressParams(long customerId, AddressRequest request) {
        return new MapSqlParameterSource().addValue("userId", customerId)
                .addValue("fullName", request.fullName().trim()).addValue("mobile", request.mobile().trim())
                .addValue("line1", request.addressLine1().trim())
                .addValue("line2", clean(request.addressLine2()), java.sql.Types.VARCHAR)
                .addValue("city", request.city().trim()).addValue("state", request.state().trim())
                .addValue("country", request.country().trim()).addValue("pincode", request.pincode().trim())
                .addValue("type", request.addressType().name());
    }

    private static AddressResponse address(ResultSet rs) throws SQLException {
        return new AddressResponse(rs.getLong("id"), rs.getString("full_name"), rs.getString("mobile"),
                rs.getString("address_line1"), rs.getString("address_line2"), rs.getString("city"),
                rs.getString("state"), rs.getString("country"), rs.getString("pincode"),
                AddressType.valueOf(rs.getString("address_type")), rs.getBoolean("is_default"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
