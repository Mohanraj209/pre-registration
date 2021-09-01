package io.mosip.preregistration.application.controller;

import java.util.Map;
import java.util.Objects;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.Errors;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.preregistration.application.constant.PreRegLoginConstant;
import io.mosip.preregistration.application.dto.OTPRequestWithLangCodeAndCaptchaToken;
import io.mosip.preregistration.application.dto.OTPWithLangCodeDTO;
import io.mosip.preregistration.application.dto.OtpRequestDTO;
import io.mosip.preregistration.application.dto.User;
import io.mosip.preregistration.application.errorcodes.ApplicationErrorCodes;
import io.mosip.preregistration.application.errorcodes.ApplicationErrorMessages;
import io.mosip.preregistration.application.exception.DeprecatedException;
import io.mosip.preregistration.application.service.LoginService;
import io.mosip.preregistration.core.common.dto.AuthNResponse;
import io.mosip.preregistration.core.common.dto.MainRequestDTO;
import io.mosip.preregistration.core.common.dto.MainResponseDTO;
import io.mosip.preregistration.core.config.LoggerConfiguration;
import io.mosip.preregistration.core.util.DataValidationUtil;
import io.mosip.preregistration.core.util.RequestValidator;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import springfox.documentation.annotations.ApiIgnore;

/**
 * This class provides different api to perform operation for login
 * 
 * @author Akshay Jain
 * @since 1.0.0
 *
 */
@RestController
@RequestMapping("/login")
@Tag(name = "Login Controller")
@CrossOrigin("*")
public class LoginController {

	/** Autowired reference for {@link #authService}. */
	@Autowired
	private LoginService loginService;

	@Autowired
	private Environment environment;

	@Value("${mosip.kernel.otp.expiry-time}")
	private int otpExpiryTime;

	@Value("${mosip.preregistration.sendotp.allowapi:false}")
	private boolean allowSendOtpApi;
	
	@Value("${server.servlet.context-path}")
	private String contextPath;

	@Autowired
	private RequestValidator loginValidator;

	/** The Constant SENDOTP. */
	private static final String SENDOTP = "preregistration.login.sendotp";

	/** The Constant VALIDATEOTP. */
	private static final String VALIDATEOTP = "preregistration.login.validateotp";

	/**
	 * Inits the binder.
	 *
	 * @param binder the binder
	 */
	@InitBinder
	public void initBinder(WebDataBinder binder) {
		binder.addValidators(loginValidator);
	}

	private Logger log = LoggerConfiguration.logConfig(LoginController.class);

	/**
	 * This Post api use to send otp to the user by email or sms
	 * 
	 * @param userOtpRequest
	 * @param errors
	 * @return AuthNResponse
	 */

	@Deprecated(since = "1.2.0", forRemoval = true)
	@PostMapping(value = "/sendOtp", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Send Otp to UserId")
	@ResponseStatus(value = HttpStatus.OK)
	public ResponseEntity<MainResponseDTO<AuthNResponse>> sendOTP(
			@Validated @RequestBody MainRequestDTO<OtpRequestDTO> userOtpRequest, @ApiIgnore Errors errors) {

		loginValidator.validateId(SENDOTP, userOtpRequest.getId(), errors);
		DataValidationUtil.validate(errors, SENDOTP);
		if (!allowSendOtpApi)
			throw new DeprecatedException(ApplicationErrorCodes.PRG_APP_008.getCode(),
					ApplicationErrorMessages.DEPRECATED_MESSAGE.getMessage());
		else
			return ResponseEntity.status(HttpStatus.OK).body(loginService.sendOTP(userOtpRequest,
					environment.getProperty(PreRegLoginConstant.MOSIP_PRIMARY_LANGUAGE)));
	}

	/**
	 * This Post api use to send otp to the user by email or sms
	 * 
	 * @param userOtpRequest
	 * @param errors
	 * @return AuthNResponse
	 */
	@PostMapping(value = "/sendOtp/langcode", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Send Otp to UserId")
	@ResponseStatus(value = HttpStatus.OK)
	public ResponseEntity<MainResponseDTO<AuthNResponse>> sendOTPWithLangCode(
			@Validated @RequestBody MainRequestDTO<OTPWithLangCodeDTO> userOtpRequest, @ApiIgnore Errors errors) {

		loginValidator.validateId(SENDOTP, userOtpRequest.getId(), errors);
		DataValidationUtil.validate(errors, SENDOTP);
		MainRequestDTO<OtpRequestDTO> MainRequestDTO = new MainRequestDTO<>();
		OtpRequestDTO dto = new OtpRequestDTO();
		dto.setUserId(userOtpRequest.getRequest().getUserId());
		MainRequestDTO.setRequest(dto);
		MainRequestDTO.setId(userOtpRequest.getId());
		MainRequestDTO.setRequesttime(userOtpRequest.getRequesttime());
		MainRequestDTO.setVersion(userOtpRequest.getVersion());
		return ResponseEntity.status(HttpStatus.OK)
				.body(loginService.sendOTP(MainRequestDTO, userOtpRequest.getRequest().getLangCode()));
	}

	/**
	 * This Post api use to validate userid and otp
	 * 
	 * @param userIdOtpRequest
	 * @param errors
	 * @return AuthNResponse
	 */
	@PostMapping(value = "/validateOtp", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Validate UserId and Otp")
	public ResponseEntity<MainResponseDTO<AuthNResponse>> validateWithUserIdOtp(
			@Validated @RequestBody MainRequestDTO<User> userIdOtpRequest, @ApiIgnore Errors errors,
			HttpServletResponse res, HttpServletRequest req) {

		log.debug("User ID: {}", userIdOtpRequest.getRequest().getUserId());
		loginValidator.validateId(VALIDATEOTP, userIdOtpRequest.getId(), errors);
		DataValidationUtil.validate(errors, VALIDATEOTP);
		Cookie responseCookie = new Cookie("Authorization",
				loginService.getLoginToken(userIdOtpRequest.getRequest().getUserId(), req.getRequestURI()));
		responseCookie.setMaxAge((int) -1);
		responseCookie.setHttpOnly(true);
		responseCookie.setSecure(true);
		//responseCookie.setPath("/");
		responseCookie.setPath("/preregistration/");
		//responseCookie.setPath("/preregistration");
		//responseCookie.setPath("/pre-registration-ui");
		//responseCookie.setPath("/pre-registration-ui/");
		res.addCookie(responseCookie);

		return ResponseEntity.status(HttpStatus.OK).body(loginService.validateWithUserIdOtp(userIdOtpRequest));
	}

	/**
	 * This Post api use to invalidate the token for logout.
	 * 
	 * @param req
	 * @return AuthNResponse
	 */
	@PostMapping(value = "/invalidateToken", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Invalidate the token")
	@ResponseStatus(value = HttpStatus.OK)
	public ResponseEntity<MainResponseDTO<String>> invalidateToken(HttpServletRequest req, HttpServletResponse res) {

		Cookie responseCookie = new Cookie("Authorization", loginService.getLogoutToken(req.getHeader("Cookie")));
		responseCookie.setMaxAge((int) -1);
		responseCookie.setHttpOnly(true);
		responseCookie.setSecure(true);
		responseCookie.setPath("/");
		res.addCookie(responseCookie);
		return ResponseEntity.status(HttpStatus.OK).body(loginService.invalidateToken(req.getHeader("Cookie")));

	}

	/**
	 * This get api is use to load the configuration data
	 * 
	 * @return the response entity
	 */

	@GetMapping(path = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Get global and Pre-Registration config data")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "global and Pre-Registration config data successfully retrieved") })
	public ResponseEntity<MainResponseDTO<Map<String, String>>> configParams() {

		return new ResponseEntity<>(loginService.getConfig(), HttpStatus.OK);

	}

	/**
	 * This get api is use to refresh the configuration data
	 * 
	 * @return the response entity
	 */
	@PreAuthorize("hasAnyRole(@authorizedRoles.getGetrefreshconfig())")
	@GetMapping(path = "/refreshconfig", produces = MediaType.APPLICATION_JSON_VALUE)
	//@PreAuthorize("hasAnyRole('PRE_REGISTRATION_ADMIN')")
	@Operation(summary = "Refresh global and Pre-Registration config data")
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "global and Pre-Registration config data successfully updated") })
	public ResponseEntity<MainResponseDTO<String>> refreshConfigParams() {

		return new ResponseEntity<>(loginService.refreshConfig(), HttpStatus.OK);

	}

	@PostMapping(path = "/sendOtpWithCaptcha", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<MainResponseDTO<?>> sendOtpWithCaptcha(
			@Validated @Valid @RequestBody MainRequestDTO<OTPRequestWithLangCodeAndCaptchaToken> sendOtpRequestWithCaptcha,
			HttpServletResponse res, @ApiIgnore Errors errors) {

		MainResponseDTO<AuthNResponse> response = loginService.validateCaptchaAndSendOtp(sendOtpRequestWithCaptcha);
		if (Objects.isNull(response.getErrors())) {
			Cookie resCookie = new Cookie("canAuthorise",
					loginService.sendOTPSuccessJwtToken(sendOtpRequestWithCaptcha.getRequest().getUserId()));
			resCookie.setMaxAge((int) otpExpiryTime / 60);
			resCookie.setHttpOnly(true);
			resCookie.setSecure(true);
			resCookie.setPath("/");
			res.addCookie(resCookie);
		}
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

}
