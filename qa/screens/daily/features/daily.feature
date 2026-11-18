@platform:android @reset:data
Feature: Daily word

  Từ của ngày: đoán từ 5 chữ cái trong 6 lần, mỗi ngày một từ (mở từ tab Chơi)
  Path: com.speakeasy.app/.MainActivity

  Background:
    Given User is on [Home] screen
    When User tap [Later Button] button
    And User tap [Play Tab] tab

  @high @spec:FR-DW-04 @spec:FR-DW-01
  Scenario: VP-UI-001 The daily word opens with the legend and the keyboard
    When User tap [Daily Word Card] item
    Then User see [Daily Screen] section
    And User see [Legend] text contains {{legend}}
    And User see [Key Z] button
    And User see [Guess Button] button

  @high @spec:FR-DW-02
  Scenario: VP-LOGIC-001 Six wrong guesses end the puzzle and reveal today's word
    When User tap [Daily Word Card] item
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Guess Button] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Guess Button] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Guess Button] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Guess Button] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Guess Button] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Guess Button] button
    Then User see [Answer Reveal] text contains {{answer_prefix}}
    And User see [Come Back Label] text contains {{come_back}}
    And User see [Guess Button] button is hidden

  @high @spec:FR-DW-03
  Scenario: VP-LOGIC-002 A finished puzzle stays finished after an app restart
    When User tap [Daily Word Card] item
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Guess Button] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Guess Button] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Guess Button] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Guess Button] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Guess Button] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Key Z] button
    And User tap top of [Guess Button] button
    And User restart the app
    And User tap [Later Button] button
    And User tap [Play Tab] tab
    And User tap [Daily Word Card] item
    Then User see [Answer Reveal] text contains {{answer_prefix}}
    And User see [Key Z] button is hidden
