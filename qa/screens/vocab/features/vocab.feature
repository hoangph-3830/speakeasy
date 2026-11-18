@platform:android @reset:data
Feature: Vocabulary

  3000 từ vựng thông dụng: tìm kiếm, nhóm từ, đánh dấu đã thuộc
  Path: com.speakeasy.app/.MainActivity

  Background:
    Given User is on [Home] screen
    When User tap [Later Button] button
    And User tap [Vocab Shortcut] button

  @high @spec:FR-VC-01
  Scenario: VP-UI-001 Vocabulary hub displays the word total and the search field
    Then User see [Vocab Screen] section
    And User see [Vocab Summary] text contains {{summary_new}}
    And User see [Search Field] field
    And User see [First Group] item

  @high @spec:FR-VC-02
  Scenario: VP-LOGIC-001 Searching an English word lists its meaning
    When User fill [Search Field] field with {{search_en}}
    Then User see [Meaning Of Person] text contains {{meaning_en}}

  @normal @spec:FR-VC-03
  Scenario: VP-LOGIC-002 Searching a Vietnamese meaning finds the English word
    When User fill [Search Field] field with {{search_vi}}
    Then User see [Word Man] text contains {{word_vi}}

  @normal @spec:FR-VC-04
  Scenario: VP-VAL-001 An unknown word shows the not-found message instead of the groups
    When User fill [Search Field] field with {{search_none}}
    Then User see [No Result Message] text contains {{no_result}}
    And User see [First Group] item is hidden

  @high @spec:FR-VC-05
  Scenario: VP-NAV-001 Opening a group shows its words and learned count
    When User tap [First Group] item
    Then User see [Vocab Detail Screen] section
    And User see [Group Progress] text contains {{group_progress_new}}
    And User see [First Word] text contains {{first_word}}

  @high @spec:FR-VC-06
  Scenario: VP-LOGIC-003 Marking a word as learned raises the group count
    When User tap [First Group] item
    And User tap [Mark Learned Button] button
    Then User see [Group Progress] text contains {{group_progress_one}}

  @high @spec:FR-VC-07
  Scenario: VP-LOGIC-004 A learned word is still counted after an app restart
    When User tap [First Group] item
    And User tap [Mark Learned Button] button
    And User restart the app
    And User dismiss [Later Button]
    And User tap [Vocab Shortcut] button
    Then User see [Vocab Summary] text contains {{summary_one}}

  @high @spec:FR-VC-08
  Scenario: VP-LOGIC-005 Adding a group to review reports the number of cards added
    When User tap [First Group] item
    And User tap [Add To Review Button] button
    Then User see [Added Message] text contains {{added_message}}

  @high @spec:FR-VC-09
  Scenario: VP-LOGIC-006 Added cards are served in Review, 30 per session
    When User tap [First Group] item
    And User tap [Add To Review Button] button
    And User restart the app
    And User tap [Later Button] button
    And User tap [Review Shortcut] button
    Then User see [Review Counter] text contains {{review_start}}

  @high @spec:FR-VC-10
  Scenario: VP-LOGIC-007 Flipping a card shows the grades and their intervals
    When User tap [First Group] item
    And User tap [Add To Review Button] button
    And User restart the app
    And User tap [Later Button] button
    And User tap [Review Shortcut] button
    And User tap [Review Card] item
    Then User see [Grade Prompt] text contains {{grade_prompt}}
    And User see [Easy Interval] text contains {{easy_interval}}
    And User see [Forget Button] button

  @high @spec:FR-VC-11
  Scenario: VP-LOGIC-008 Grading Nhớ moves the card out of today's queue
    When User tap [First Group] item
    And User tap [Add To Review Button] button
    And User restart the app
    And User tap [Later Button] button
    And User tap [Review Shortcut] button
    And User tap [Review Card] item
    And User tap top of [Remember Button] button
    Then User see [Review Counter] text contains {{review_after_remember}}

  @high @spec:FR-VC-12
  Scenario: VP-LOGIC-009 Grading Quên keeps the card in today's queue
    When User tap [First Group] item
    And User tap [Add To Review Button] button
    And User restart the app
    And User tap [Later Button] button
    And User tap [Review Shortcut] button
    And User tap [Review Card] item
    And User tap top of [Forget Button] button
    Then User see [Review Counter] text contains {{review_start}}
