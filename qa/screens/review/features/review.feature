@platform:android @reset:data
Feature: Review

  Ôn tập lặp lại ngắt quãng (mở từ phím tắt Ôn tập)
  Path: com.speakeasy.app/.MainActivity

  Background:
    Given User is on [Home] screen
    When User tap [Later Button] button
    And User tap [Review Shortcut] button

  @high @spec:FR-RV-01
  Scenario: VP-UI-001 A new learner sees the empty review state
    Then User see [Review Screen] section
    And User see [Empty Title] text contains {{empty_title}}
    And User see [Empty Hint] text contains {{empty_hint}}
    And User see [Back Home Button] button

  @high @spec:FR-RV-02
  Scenario: VP-NAV-001 "Về trang chủ" returns to Home
    When User tap [Back Home Button] button
    And User dismiss [Later Button]
    Then User see [Home Screen] section

  @normal @spec:FR-RV-03
  Scenario: VP-NAV-002 The back arrow returns to Home
    When User tap [Back Button] button
    And User dismiss [Later Button]
    Then User see [Home Screen] section
