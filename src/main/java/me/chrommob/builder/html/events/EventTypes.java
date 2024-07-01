package me.chrommob.builder.html.events;

public enum EventTypes {

    BEFORELOAD(EventBuilders.justRun, null),
    BEFOREUNLOAD(EventBuilders.global, false),
    MOUSEOVER(EventBuilders.perElement, false),
    MOUSEOUT(EventBuilders.perElement, false),
    CLICK(EventBuilders.perElement, true),
    LOAD(EventBuilders.global, false),
    KEYDOWN(EventBuilders.perElementWithEventValue, "key"),
    TENSECONDTIMER(EventBuilders.timer, "10000"),
    SECONDTIMER(EventBuilders.timer, "1000"),
    CHANGE(EventBuilders.getFile, null),
    DROP(EventBuilders.perElement, true),
    TOUCHEND(EventBuilders.perElement, true),
    DRAGOVER(EventBuilders.perElement, true),
    DRAGSTART(EventBuilders.perElement, false),
    TOUCHSTART(EventBuilders.perElement, false);

    private final EventBuilders.EventBuilder eventBuilders;
    private final Object extraData;
    EventTypes(EventBuilders.EventBuilder eventBuilders, Object extraData) {
        this.eventBuilders = eventBuilders;
        this.extraData = extraData;
    }

    public String build() {
        return eventBuilders.build(this, extraData);
    }

    public String buildForName(String id) {
        return eventBuilders.buildForName(this, id, extraData);
    }
}