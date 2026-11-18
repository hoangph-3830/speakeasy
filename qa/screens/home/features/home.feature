@platform:android @reset:data
Feature: Home screen

  As a learner
  I want the Home tab to greet me and lead me to my next lesson
  So that I can start studying in one tap
  Path: com.speakeasy.app/.MainActivity

  Background:
    Given User is on [Home] screen

  @high @spec:FR-HOME-01
  Scenario: VP-ONB-001 First launch asks for the learner's name
    Then User see [Name Dialog] dialog
    And User see [Name Input] field
    And User see [Start Button] button
    And User see [Later Button] button

  @high @spec:FR-HOME-02
  Scenario: VP-ONB-002 A saved name appears in the greeting
    When User fill [Name Input] field with {{learner_name}}
    And User tap [Start Button] button
    Then User see [Greeting] text contains {{learner_name}}

  @high @spec:FR-HOME-02
  Scenario: VP-ONB-005 A saved name survives an app restart
    When User fill [Name Input] field with {{learner_name}}
    And User tap [Start Button] button
    And User restart the app
    Then User see [Name Dialog] dialog is hidden
    And User see [Greeting] text contains {{learner_name}}

  @high @spec:FR-HOME-03
  Scenario: VP-ONB-003 Later closes the name dialog and shows Home
    When User tap [Later Button] button
    Then User see [Name Dialog] dialog is hidden
    And User see [Home Screen] section
    And User see [Greeting] text contains {{greeting_suffix}}

  @high @spec:FR-HOME-04 @spec:FR-HOME-05 @spec:FR-HOME-06
  Scenario: VP-UI-001 Goal card, continue button and shortcut row are displayed on load
    When User tap [Later Button] button
    Then User see [Daily Goal] text contains {{new_goal_text}}
    And User see [Continue Learning] button
    And User see [Review Shortcut] button
    And User see [Vocab Shortcut] button
    And User see [Lab Shortcut] button
    And User see [Stories Shortcut] button
    And User see [News Shortcut] button
    And User see [Videos Shortcut] button

  @high @spec:FR-HOME-04
  Scenario: VP-LOGIC-001 A new learner starts at zero XP of the daily goal
    When User tap [Later Button] button
    Then User see [Daily Goal] text contains {{new_goal_text}}

  @high @spec:FR-HOME-05
  Scenario: VP-LOGIC-002 Continue card points at the first topic and opens it
    When User tap [Later Button] button
    Then User see [First Topic Title] text contains {{first_topic_title}}
    When User tap [Continue Learning] button
    Then User see [Topic Screen] section
    And User see [Topic Back] button

  @high @spec:FR-HOME-07
  Scenario: VP-NAV-001 Placement card opens the placement test
    When User tap [Later Button] button
    And User tap [Placement Card] button
    Then User see [Placement Screen] section

  @high @spec:FR-HOME-06
  Scenario: VP-NAV-002 Review shortcut opens the review screen
    When User tap [Later Button] button
    And User tap [Review Shortcut] button
    Then User see [Review Screen] section

  @high @spec:FR-HOME-06
  Scenario: VP-NAV-003 Vocabulary shortcut opens the vocabulary hub
    When User tap [Later Button] button
    And User tap [Vocab Shortcut] button
    Then User see [Vocab Screen] section

  @high @spec:FR-HOME-06
  Scenario: VP-NAV-004 Pronunciation shortcut opens the pronunciation lab
    When User tap [Later Button] button
    And User tap [Lab Shortcut] button
    Then User see [Lab Screen] section

  @high @spec:FR-HOME-06
  Scenario: VP-NAV-005 Stories shortcut opens the stories hub
    When User tap [Later Button] button
    And User tap [Stories Shortcut] button
    Then User see [Stories Screen] section

  @high @spec:FR-HOME-06
  Scenario: VP-NAV-006 News shortcut opens the news tab
    When User tap [Later Button] button
    And User tap [News Shortcut] button
    Then User see [News Screen] section

  @high @spec:FR-HOME-06
  Scenario: VP-NAV-007 Video shortcut opens the video hub
    When User tap [Later Button] button
    And User tap [Videos Shortcut] button
    Then User see [Videos Screen] section

  @normal @spec:FR-HOME-08
  Scenario: VP-LOGIC-003 Switching to "by topic" lists topics with category chips
    When User tap [Later Button] button
    And User tap [By Topic Switch] button
    Then User see [All Categories Chip] button
    And User see [First Topic Row] item

  @high @spec:FR-HOME-09
  Scenario: VP-NAV-008 Bottom tabs switch between the main screens
    When User tap [Later Button] button
    And User tap [Play Tab] tab
    Then User see [Play Screen] section
    When User tap [AI Tab] tab
    Then User see [AI Screen] section
    When User tap [Progress Tab] tab
    Then User see [Progress Screen] section

  # Defect: "Để sau" is only kept while Home stays composed, so coming back to the
  # Home tab asks for the name again.
  @high @spec:FR-HOME-03 @known-defect:DEF-001
  Scenario: VP-ONB-004 Home tab shows Home again after choosing Later
    When User tap [Later Button] button
    And User tap [Progress Tab] tab
    And User tap [Home Tab] tab
    Then User see [Name Dialog] dialog is hidden
    And User see [Home Screen] section
