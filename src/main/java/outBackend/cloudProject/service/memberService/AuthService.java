package outBackend.cloudProject.service.memberService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import outBackend.cloudProject.apiPayload.code.status.ErrorStatus;
import outBackend.cloudProject.apiPayload.exception.handler.SkillTagHandler;
import outBackend.cloudProject.converter.MemberConverter;
import outBackend.cloudProject.converter.MemberSkillTagConverter;
import outBackend.cloudProject.domain.Member;
import outBackend.cloudProject.domain.SkillTag;
import outBackend.cloudProject.domain.mapping.MemberSkillTag;
import outBackend.cloudProject.repository.MemberRepository;
import outBackend.cloudProject.repository.RefreshTokenRepository;
import outBackend.cloudProject.repository.SkillTagRepository;
import outBackend.cloudProject.security.RefreshToken;
import outBackend.cloudProject.security.TokenProvider;
import outBackend.cloudProject.dto.MemberRequestDTO;
import outBackend.cloudProject.security.TokenDto;
import outBackend.cloudProject.dto.TokenRequestDTO;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class AuthService {
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SkillTagRepository skillTagRepository;

    @Transactional
    public Member signup(MemberRequestDTO.JoinDTO joinRequest) {
        if (memberRepository.existsByEmail(joinRequest.getEmail())) {
            throw new RuntimeException("이미 가입되어 있는 유저입니다");
        }

        Member member = MemberConverter.toMember(joinRequest, passwordEncoder);

        List<SkillTag> skillTagList = joinRequest.getSkillTagList().stream()
                .map(skillTag -> {
                    return skillTagRepository.findByName(skillTag).orElseThrow(() -> new SkillTagHandler(ErrorStatus._SKILLTAG_NOT_FOUND));
                }).collect(Collectors.toList());

        List<MemberSkillTag> memberSkillTagList = MemberSkillTagConverter.toMemberSkillTagList(skillTagList);

        memberSkillTagList.forEach(memberSkillTag -> {
            memberSkillTag.setMember(member);
            memberSkillTag.setSkillTag(memberSkillTag.getSkillTag());
        });

        return memberRepository.save(member);
    }

    @Transactional
    public TokenDto login(MemberRequestDTO.LoginDTO loginRequest) {
        // 1. Login ID/PW 를 기반으로 AuthenticationToken 생성
        UsernamePasswordAuthenticationToken authenticationToken = MemberConverter.toAuthentication(loginRequest);

        // 2. 실제로 검증 (사용자 비밀번호 체크) 이 이루어지는 부분
        //    authenticate 메서드가 실행이 될 때 CustomUserDetailsService 에서 만들었던 loadUserByUsername 메서드가 실행됨
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        // 3. 인증 정보를 기반으로 JWT 토큰 생성
        TokenDto tokenDto = tokenProvider.generateTokenDto(authentication);

        // 4. RefreshToken 저장
        RefreshToken refreshToken = RefreshToken.builder()
                .key(authentication.getName())
                .value(tokenDto.getRefreshToken())
                .build();

        refreshTokenRepository.save(refreshToken);

        // 5. 토큰 발급
        return tokenDto;
    }

    @Transactional
    public TokenDto reissue(TokenRequestDTO tokenRequestDto) {
        // 1. Refresh Token 검증
        if (!tokenProvider.validateToken(tokenRequestDto.getRefreshToken())) {
            throw new RuntimeException("Refresh Token 이 유효하지 않습니다.");
        }

        // 2. Access Token 에서 Member ID 가져오기
        Authentication authentication = tokenProvider.getAuthentication(tokenRequestDto.getAccessToken());

        // 3. 저장소에서 Member ID 를 기반으로 Refresh Token 값 가져옴
        RefreshToken refreshToken = refreshTokenRepository.findByKey(authentication.getName())
                .orElseThrow(() -> new RuntimeException("로그아웃 된 사용자입니다."));

        // 4. Refresh Token 일치하는지 검사
        if (!refreshToken.getValue().equals(tokenRequestDto.getRefreshToken())) {
            throw new RuntimeException("토큰의 유저 정보가 일치하지 않습니다.");
        }

        // 5. 새로운 토큰 생성
        TokenDto tokenDto = tokenProvider.generateTokenDto(authentication);

        // 6. 저장소 정보 업데이트
        RefreshToken newRefreshToken = refreshToken.updateValue(tokenDto.getRefreshToken());
        refreshTokenRepository.save(newRefreshToken);

        // 토큰 발급
        return tokenDto;
    }
}
