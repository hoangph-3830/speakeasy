@platform:android @reset:data
Feature: Placement test

  Bài kiểm tra trình độ (mở từ thẻ trên Home hoặc màn Tiến độ)
  Path: com.speakeasy.app/.MainActivity

  Background:
    Given User is on [Home] screen
    When User tap [Later Button] button
    And User tap [Placement Card] button

  @high @spec:FR-PL-01
  Scenario: VP-UI-001 Placement test displays an A1 question with answer options
    Then User see [Placement Screen] section
    And User see [Level Pill] text contains {{first_level}}
    And User see [Question Hint] text contains {{question_hint}}
    And User see [Answer Option] button

  @high @spec:FR-PL-02
  Scenario: VP-LOGIC-001 After one answer the test stays at A1 with the next question
    When User tap [Answer Option] button
    And User wait for 2 seconds
    Then User see [Level Pill] text contains {{first_level}}
    And User see [Answer Option] button is enabled

  @high @spec:FR-PL-03
  Scenario: VP-NAV-001 Leaving the test early keeps the placement card on Home
    When User tap [Back Button] button
    And User dismiss [Later Button]
    Then User see [Home Screen] section
    And User see [Placement Card] button

  @high @spec:FR-PL-04 @manual:M7
  Scenario: VP-LOGIC-002 Finishing the test saves the level and Home hides the placement card
    # Manual: the questions and their answer order are shuffled at random on every run,
    # so the number of answers needed to finish cannot be scripted.
    When User tap [Answer Option] button
    Then User see [Result Title] text contains {{result_prefix}}
    When User tap [Result Continue] button
    Then User see [Placement Card] button is hidden
