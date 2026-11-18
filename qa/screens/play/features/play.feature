@platform:android @reset:data
Feature: Play hub and vocabulary challenges

  Tab Chơi: các thử thách từ vựng (Đua 60 giây, Lật thẻ, Đúng hay Sai, Nghe & viết…)
  Path: com.speakeasy.app/.MainActivity

  Background:
    Given User is on [Home] screen
    When User tap [Later Button] button
    And User tap [Play Tab] tab

  @high @spec:FR-PY-01
  Scenario: VP-UI-001 Play tab displays the vocabulary challenges
    Then User see [Play Screen] section
    And User see [Race Card] text contains {{race_title}}
    And User see [Memory Card] text contains {{memory_title}}
    And User see [True False Card] text contains {{true_false_title}}

  @high @spec:FR-PY-02
  Scenario: VP-NAV-001 The 60-second race starts at score 0 with answer options
    When User tap [Race Card] item
    Then User see [Challenge Screen] section
    And User see [Score Label] text contains {{score_zero}}
    And User see [Timer Label] text contains {{timer_unit}}
    And User see [Answer Option] button

  @normal @spec:FR-PY-03
  Scenario: VP-NAV-002 Memory cards open the memory challenge
    When User tap [Memory Card] item
    Then User see [Challenge Screen] section
    And User see [Memory Title] text contains {{memory_title}}

  @normal @spec:FR-PY-04
  Scenario: VP-NAV-003 True or false opens with answer options
    When User tap [True False Card] item
    Then User see [Challenge Screen] section
    And User see [True False Title] text contains {{true_false_title}}

  @normal @spec:FR-PY-05
  Scenario: VP-NAV-004 Listen and spell opens with a field to type the word
    When User scroll to [Spelling Card] item
    And User tap [Spelling Card] item
    Then User see [Challenge Screen] section
    And User see [Spelling Field] field

  @normal @spec:FR-PY-06
  Scenario: VP-NAV-005 Back from a challenge returns to the Play tab
    When User tap [Race Card] item
    And User tap [Back Button] button
    Then User see [Play Screen] section

  @high @spec:FR-PY-07
  Scenario: VP-NAV-006 Write mode opens on a Vietnamese prompt with an answer field
    When User scroll to [Write Mode Card] item
    And User tap [Write Mode Card] item
    Then User see [Game Screen] section
    And User see [Write Title] text contains {{write_title}}
    And User see [Game Input] field

  @high @spec:FR-PY-08
  Scenario: VP-VAL-001 Check is disabled while the required answer is empty
    When User scroll to [Write Mode Card] item
    And User tap [Write Mode Card] item
    Then User see [Check Button] button is disabled
    When User fill [Game Input] field with {{write_answer}}
    Then User see [Game Input] field with {{write_answer}}
    And User see [Check Button] button is enabled

  @high @spec:FR-PY-09
  Scenario: VP-LOGIC-001 Submitting an answer shows the model sentence and a match score
    When User scroll to [Write Mode Card] item
    And User tap [Write Mode Card] item
    And User fill [Game Input] field with {{write_answer}}
    And User tap [Check Button] button
    Then User see [Model Answer Label] label matches "Câu mẫu · khớp X%"

  @normal @spec:FR-PY-08
  Scenario: VP-VAL-002 A whitespace-only answer counts as empty (format boundary)
    When User scroll to [Write Mode Card] item
    And User tap [Write Mode Card] item
    And User fill [Game Input] field with {{blank_answer}}
    Then User see [Check Button] button is disabled
    And User see [Model Answer Label] text is hidden

  @normal @spec:FR-PY-10
  Scenario: VP-LOGIC-002 The hint reveals how the answer starts
    When User scroll to [Write Mode Card] item
    And User tap [Write Mode Card] item
    Then User see [Hint Text] text is hidden
    When User tap [Hint Link] button
    Then User see [Hint Text] text contains {{hint_prefix}}
