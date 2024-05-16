package neobis.cookscorner.exception;

public class RegistrationTokenExpiredException extends RuntimeException{
    public RegistrationTokenExpiredException(String message) {
        super(message);
    }

}
