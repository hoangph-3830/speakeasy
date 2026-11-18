@platform:android @reset:data
Feature: Topic lesson

  Màn chủ đề (Meeting new people) và 4 bước học: cụm từ, hội thoại, luyện nói, game; luyện nhại
  Path: com.speakeasy.app/.MainActivity

  Background:
    Given User is on [Home] screen
    When User tap [Later Button] button
    And User tap [Continue Learning] button

  @high @spec:FR-TP-01
  Scenario: VP-UI-001 Topic detail screen shows the title, counts and learning steps
    Then User see [Topic Screen] section
    And User see [Topic Title] text contains {{topic_title}}
    And User see [Topic Counts] text contains {{topic_counts}}
    And User see [Step Phrases] button
    And User see [Step Dialogue] button
    And User see [Step Speak] button
    And User see [Step Game] button

  @high @spec:FR-TP-02
  Scenario: VP-NAV-001 The phrases step opens with the topic's first phrase
    When User tap [Step Phrases] button
    Then User see [Phrases Screen] section
    And User see [First Phrase] text contains {{first_phrase}}
    And User see [Speak Button] button

  @high @spec:FR-TP-03
  Scenario: VP-NAV-002 The dialogue step opens with Alex's first line
    When User tap [Step Dialogue] button
    Then User see [Dialogue Screen] section
    And User see [Alex Line] text contains {{alex_line}}

  @high @spec:FR-TP-04
  Scenario: VP-NAV-003 The speaking step opens practice with the microphone
    When User tap [Step Speak] button
    Then User see [Speak Screen] section
    And User see [Mic Button] button

  @high @spec:FR-TP-05
  Scenario: VP-NAV-004 The game step opens the topic game at its first round
    When User tap [Step Game] button
    Then User see [Game Screen] section
    And User see [Round Counter] text contains {{first_round}}

  @normal @spec:FR-TP-06
  Scenario: VP-NAV-005 Shadowing card opens shadowing practice
    When User tap [Shadowing Card] button
    Then User see [Shadow Screen] section

  @high @spec:FR-TP-07
  Scenario: VP-LOGIC-001 Finishing the vocabulary cards completes the phrases step and adds 10 XP
    When User tap [Step Phrases] button
    And User tap [Vocab Cards Tab] button
    And User tap [Next Card Button] button
    And User tap [Next Card Button] button
    And User tap [Next Card Button] button
    And User tap [Next Card Button] button
    And User tap [Next Card Button] button
    And User tap [Finish Button] button
    Then User see [Complete Dialog] dialog
    And User see [Complete Title] text contains {{complete_title}}
    When User tap [Dialog Continue Button] button
    And User restart the app
    And User tap [Later Button] button
    Then User see [Daily Goal] text contains {{goal_after_phrases}}

  @high @spec:FR-TP-08
  Scenario: VP-LOGIC-002 Completing the phrases step fills the review deck
    When User tap [Step Phrases] button
    And User tap [Vocab Cards Tab] button
    And User tap [Next Card Button] button
    And User tap [Next Card Button] button
    And User tap [Next Card Button] button
    And User tap [Next Card Button] button
    And User tap [Next Card Button] button
    And User tap [Finish Button] button
    And User tap [Dialog Continue Button] button
    And User restart the app
    And User tap [Later Button] button
    And User tap [Review Shortcut] button
    Then User see [Review Counter] text contains {{review_counter_prefix}}
    And User see [Review Empty Title] text is hidden

  @high @spec:FR-TP-01
  Scenario: VP-LOGIC-003 Topic detail shows the same title as the continue card on Home (consistency)
    When User tap [Back Button] button
    And User dismiss [Later Button]
    And User remember [Home Topic Title] text as {{card_topic}}
    And User tap [Continue Learning] button
    Then User see [Topic Title] text contains {{card_topic}}

  @normal @spec:FR-TP-01
  Scenario: VP-NAV-006 Back from the topic returns to Home
    When User tap [Back Button] button
    And User dismiss [Later Button]
    Then User see [Home Screen] section
