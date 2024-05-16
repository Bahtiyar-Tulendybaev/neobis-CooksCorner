package neobis.cookscorner.service;

import neobis.cookscorner.entity.User;
import neobis.cookscorner.entity.dto.request.LoginRequest;
import neobis.cookscorner.entity.dto.request.RegistrationRequest;
import neobis.cookscorner.entity.dto.response.LoginResponse;
import neobis.cookscorner.entity.enums.Role;
import neobis.cookscorner.entity.enums.UserState;
import neobis.cookscorner.exception.IncorrectLoginException;
import neobis.cookscorner.exception.NotFoundException;
import neobis.cookscorner.exception.RegistrationTokenExpiredException;
import neobis.cookscorner.exception.UserAlreadyExistException;
import neobis.cookscorner.repository.UserRepository;
import neobis.cookscorner.security.jwt.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    @Value("${spring.gmail.username}")
    private String mail;
    private final JavaMailSender javaMailSender;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder;
    private final JwtTokenUtil jwtTokenUtil;
    String mailText = "Please click to link in below to finish registration!";

    public String registration(RegistrationRequest request) {
        if (userRepository.findByUniqueConstraint(request.getUsername(), request.getEmail()).isPresent()) {
            throw new UserAlreadyExistException("User with username = " + request.getEmail() + " already exist");
        }
        userRepository.save(mapUserRequestToUser(request));
        return "User successfully saved!";
    }

    public String sendMessage(RegistrationRequest request, String link) {
        User user = userRepository.findByUniqueConstraint(request.getUsername(), request.getEmail()).orElseThrow(
                () -> new NotFoundException("User with email = " + request.getEmail() + " not exist")
        );
        String UUID = java.util.UUID.randomUUID().toString();
        sendSimpleMessage(request.getEmail(), link, UUID);
        user.setUUIDExpirationDate(LocalDateTime.now().plusMinutes(5));
        user.setUUID(UUID);
        userRepository.save(user);
        return "The message has been sent by mail " + request.getEmail()+ "\n token: " +  UUID;
    }


    public String confirmRegistration(String UUID) {
        User user = userRepository.findByUUID(UUID).orElseThrow(
                () -> new NotFoundException("User is not found by UUID = " + UUID)
        );
        if (user.getUUIDExpirationDate().isBefore(LocalDateTime.now())) {
            userRepository.delete(user);
            throw new RegistrationTokenExpiredException("Your registration token got expired!");
        }
        user.setState(UserState.ACTIVATED);
        user.setUUID(null);
        user.setUUIDExpirationDate(null);
        userRepository.save(user);
        return "User account successfully activated";
    }

    public LoginResponse login(LoginRequest loginRequest) {
        User existUser = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new NotFoundException("User not found by username = " + loginRequest.getUsername()));
        if (encoder.matches(loginRequest.getPassword(), existUser.getPassword()) && existUser.getState() == UserState.ACTIVATED) {
            return loginView(jwtTokenUtil.generateToken(existUser), existUser);
        } else {
            throw new IncorrectLoginException("Password is not correct or Access denied! You are not registered");
        }
    }

    private User mapUserRequestToUser(RegistrationRequest request) {
        return User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .role(Role.USER)
                .UUIDExpirationDate(LocalDateTime.now().plusMinutes(5))
                .state(UserState.DISABLED)
                .password(encoder.encode(request.getPassword()))
                .build();
    }

    public void sendSimpleMessage(String email, String link, String uuid) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setFrom(mail);
        message.setSubject("registration!");
        message.setTo(email);
        message.setText(mailText + "\n" + link + "?token=" + uuid);
        javaMailSender.send(message);
    }

    public LoginResponse loginView(String token, User user) {
        return LoginResponse.builder()
                .jwt(token)
                .username(user.getUsername())
                .authorities(user.getAuthorities().toString())
                .build();
    }
}