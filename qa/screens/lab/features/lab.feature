@platform:android @reset:data
Feature: Pronunciation lab

  Phòng phát âm: danh sách bài và bài học theo bước
  Path: com.speakeasy.app/.MainActivity

  Background:
    Given User is on [Home] screen
    When User tap [Later Button] button
    And User tap [Lab Shortcut] button

  @high @spec:FR-LB-01
  Scenario: VP-UI-001 Lab displays the score card and the lesson list
    Then User see [Lab Screen] section
    And User see [Score Hint] text contains {{score_hint}}
    And User see [First Lesson] text contains {{first_lesson}}

  @high @spec:FR-LB-02
  Scenario: VP-NAV-001 A lesson opens at step 1 of 4 with minimal pairs
    When User tap [First Lesson] item
    Then User see [Lab Lesson Screen] section
    And User see [Step Counter] text contains {{step_one}}
    And User see [Pair Word] text contains {{pair_word}}
    And User see [Speak Button] button

  @high @spec:FR-LB-03
  Scenario: VP-LOGIC-001 Starting practice moves the lesson to step 2
    When User tap [First Lesson] item
    And User tap [Start Practice Button] button
    Then User see [Step Counter] text contains {{step_two}}

  @normal @spec:FR-LB-04
  Scenario: VP-NAV-002 Back from a lesson returns to the lab list
    When User tap [First Lesson] item
    And User tap [Back Button] button
    Then User see [Lab Screen] section

  @high @spec:FR-LB-05
  Scenario: VP-NAV-003 The IPA chart opens with the 44 sounds
    When User tap [IPA Chart Button] button
    Then User see [IPA Screen] section
    And User see [IPA Count] text contains {{ipa_count}}

  @high @spec:FR-LB-06
  Scenario: VP-LOGIC-002 Tapping /ɪ/ shows its example word and tip
    When User tap [IPA Chart Button] button
    And User tap [IPA Sit Example] item
    Then User see [IPA Tip] text contains {{ipa_tip}}
    And User see [Speak Button] button

  @high @spec:FR-LB-07
  Scenario: VP-NAV-004 Tongue twisters open with the first easy twister
    When User tap [Twisters Button] button
    Then User see [Twisters Screen] section
    And User see [First Twister] text contains {{first_twister}}
    And User see [First Twister Focus] text contains {{first_twister_focus}}

  @high @spec:FR-LB-08
  Scenario: VP-LOGIC-003 Opening a twister shows its meaning and the microphone
    When User tap [Twisters Button] button
    And User tap [First Twister] item
    Then User see [Twister Meaning] text contains {{twister_meaning}}
    And User see [Mic Button] button
