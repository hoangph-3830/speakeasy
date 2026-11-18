@platform:android @reset:data
Feature: AI conversation

  Tab Nói với AI: chọn độ khó, chọn tình huống, trò chuyện
  Path: com.speakeasy.app/.MainActivity

  Background:
    Given User is on [Home] screen
    When User tap [Later Button] button
    And User tap [AI Tab] tab

  @high @spec:FR-AI-01
  Scenario: VP-UI-001 AI tab displays difficulty levels and scenarios
    Then User see [AI Screen] section
    And User see [Easy Level] button
    And User see [Native Level] button
    And User see [Free Talk Scenario] text contains {{free_talk}}
    And User see [Interview Scenario] text contains {{interview}}

  @high @spec:FR-AI-02
  Scenario: VP-NAV-001 Free talk opens the chat with Alex
    When User tap [Free Talk Scenario] item
    Then User see [AI Chat Screen] section
    And User see [Role Label] text contains {{role_alex}}
    And User see [Chat Input] field
    And User see [Mic Button] button

  @high @spec:FR-AI-03
  Scenario: VP-VAL-001 Send is disabled while the message is empty
    When User tap [Free Talk Scenario] item
    Then User see [Send Button] button is disabled

  @normal @spec:FR-AI-04 @manual:M5
  Scenario: VP-LOGIC-001 The AI replies to a typed message
    # Manual: depends on the external AI service and its key; the reply is generated text.
    When User tap [Free Talk Scenario] item
    And User fill [Chat Input] field with {{message}}
    And User tap [Send Button] button
    Then User see [AI Reply] text contains {{reply_hint}}
