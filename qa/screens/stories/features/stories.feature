@platform:android @reset:data
Feature: Stories

  Truyện nghe và truyện tranh song ngữ
  Path: com.speakeasy.app/.MainActivity

  Background:
    Given User is on [Home] screen
    When User tap [Later Button] button
    And User tap [Stories Shortcut] button

  @high @spec:FR-ST-01
  Scenario: VP-UI-001 Audio stories are listed with their counts
    Then User see [Stories Screen] section
    And User see [First Story Title] text contains {{first_story}}
    And User see [First Story Counts] text contains {{first_story_counts}}

  @high @spec:FR-ST-02
  Scenario: VP-LOGIC-001 The picture-book tab lists books with page counts
    When User tap [Picture Books Tab] tab
    Then User see [First Book Title] text contains {{first_book}}
    And User see [Book Pages] text contains {{book_pages}}

  @high @spec:FR-ST-03
  Scenario: VP-NAV-001 A story opens on its first sentence with the translation
    When User tap [First Story Title] item
    Then User see [Story Screen] section
    And User see [First Sentence] text contains {{first_sentence}}
    And User see [First Translation] text contains {{first_translation}}

  @normal @spec:FR-ST-04
  Scenario: VP-NAV-002 A picture book opens the reader
    When User tap [Picture Books Tab] tab
    And User tap [First Book Title] item
    Then User see [Book Screen] section

  @high @spec:FR-ST-05
  Scenario: VP-LOGIC-002 Continue reveals the next line of the story
    When User tap [First Story Title] item
    Then User see [Mia Line] text is hidden
    When User tap [Story Continue Button] button
    Then User see [Mia Line] text contains {{mia_line}}
