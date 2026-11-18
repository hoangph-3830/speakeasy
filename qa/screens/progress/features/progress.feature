@platform:android @reset:data
Feature: Progress and settings

  Tab Tiến độ: thống kê, mục tiêu ngày, giọng đọc, nhắc học, làm lại kiểm tra trình độ, xoá tiến độ
  Path: com.speakeasy.app/.MainActivity

  Background:
    Given User is on [Home] screen
    When User tap [Later Button] button
    And User tap [Progress Tab] tab

  @high @spec:FR-PG-01
  Scenario: VP-UI-001 A new learner's progress is displayed at level 1 with 0 XP
    Then User see [Progress Screen] section
    And User see [Level Label] text contains {{level_one}}
    And User see [Level XP] text contains {{level_xp_zero}}
    And User see [Learned Words Label] text contains {{learned_label}}

  @high @spec:FR-PG-02
  Scenario: VP-LOGIC-001 Choosing the 50 XP goal moves the selection from 30 to 50 XP
    When User scroll to [Goal 30 Option] item
    Then User see [Goal 30 Option] item is checked
    When User tap [Goal 50 Option] item
    Then User see [Goal 50 Option] item is checked
    And User see [Goal 30 Option] item is unchecked

  @high @spec:FR-PG-02 @spec:FR-PG-07
  Scenario: VP-LOGIC-002 The 50 XP goal is shown on Home after an app restart
    When User scroll to [Goal 50 Option] item
    And User tap [Goal 50 Option] item
    And User restart the app
    And User tap [Later Button] button
    Then User see [Daily Goal] text contains {{goal_50}}

  @high @spec:FR-PG-08
  Scenario: VP-LOGIC-006 Turning the daily reminder on shows the reminder time
    When User grant [Notifications] permission
    And User scroll to [Reminder Switch] toggle
    And User check [Reminder Switch] toggle
    Then User see [Reminder Switch] toggle is checked
    And User see [Reminder Time] text contains {{reminder_time}}

  @normal @spec:FR-PG-03
  Scenario: VP-LOGIC-003 Choosing the UK accent shows it as selected
    When User scroll to [UK Accent Chip] button
    And User tap [UK Accent Chip] button
    Then User see [UK Accent Chip] button is checked

  @high @spec:FR-PG-04
  Scenario: VP-LOGIC-004 Confirming the reset puts the daily goal back to 30 XP
    When User scroll to [Goal 50 Option] item
    And User tap [Goal 50 Option] item
    And User scroll to [Reset Button] button
    And User tap [Reset Button] button
    Then User see [Reset Dialog] dialog
    When User tap [Reset Confirm] button
    And User restart the app
    And User tap [Later Button] button
    Then User see [Daily Goal] text contains {{goal_30}}

  @high @spec:FR-PG-05
  Scenario: VP-LOGIC-005 Cancelling the reset keeps the 50 XP goal
    When User scroll to [Goal 50 Option] item
    And User tap [Goal 50 Option] item
    And User scroll to [Reset Button] button
    And User tap [Reset Button] button
    And User tap [Reset Cancel] button
    Then User see [Reset Dialog] dialog is hidden
    And User restart the app
    And User tap [Later Button] button
    Then User see [Daily Goal] text contains {{goal_50}}

  @normal @spec:FR-PG-06
  Scenario: VP-NAV-001 Retaking the placement test opens it
    When User scroll to [Retake Placement Button] button
    And User tap [Retake Placement Button] button
    Then User see [Placement Screen] section

  @high @spec:FR-PG-09
  Scenario: VP-LOGIC-007 The kids' path makes Home continue with a kids' topic
    When User scroll to [Kids Path Option] button
    And User tap [Kids Path Option] button
    And User restart the app
    And User tap [Later Button] button
    Then User see [Next Stage Label] text contains {{kids_stage}}
    And User see [Kids First Topic] text contains {{kids_first_topic}}
