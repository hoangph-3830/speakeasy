@platform:android @reset:data
Feature: Videos

  Học qua video từ kênh chính thức trên YouTube (cần mạng)
  Path: com.speakeasy.app/.MainActivity

  Background:
    Given User is on [Home] screen
    When User tap [Later Button] button
    And User tap [Videos Shortcut] button

  @high @spec:FR-VD-01
  Scenario: VP-UI-001 Video hub displays the official channels
    Then User see [Videos Screen] section
    And User see [Videos Title] text contains {{videos_title}}
    And User see [First Channel] text contains {{first_channel}}

  @normal @spec:FR-VD-02
  Scenario: VP-NAV-001 Opening a video opens the player
    When User tap [First Video] item
    Then User see [Video Screen] section
