@platform:android @reset:data
Feature: News

  Đọc báo song ngữ từ BBC, CNN, Reuters… (cần mạng)
  Path: com.speakeasy.app/.MainActivity

  Background:
    Given User is on [Home] screen
    When User tap [Later Button] button
    And User tap [News Tab] tab

  @high @spec:FR-NW-01
  Scenario: VP-UI-001 News displays the sources and categories
    Then User see [News Screen] section
    And User see [BBC Source] button
    And User see [CNN Source] button
    And User see [Featured Category] text contains {{featured}}

  @high @spec:FR-NW-02
  Scenario: VP-LOGIC-001 Choosing CNN lists only CNN articles
    When User tap [CNN Source] button
    And User wait for 3 seconds
    Then User see all [Article Source Label] text contains {{cnn}}

  @high @spec:FR-NW-04
  Scenario: VP-LOGIC-002 Every article row on the default list is from BBC
    Then User see all [Article Source Label] text contains {{bbc}}

  @normal @spec:FR-NW-05
  Scenario: VP-LOGIC-003 After refresh the list still shows BBC articles
    When User tap [Refresh Button] button
    And User wait for 3 seconds
    Then User see [Updated Label] text contains {{updated_prefix}}
    And User see all [Article Source Label] text contains {{bbc}}

  @normal @spec:FR-NW-04
  Scenario: VP-LOGIC-004 After scrolling down the feed, the rows are still BBC articles
    When User swipe up on [Article List] list
    And User swipe up on [Article List] list
    Then User see all [Article Source Label] text contains {{bbc}}

  @normal @spec:FR-NW-03
  Scenario: VP-NAV-001 Opening an article opens the reader
    When User tap [Article Source Label] item
    Then User see [Article Screen] section
