package me.chrommob.builder.html.events;

public enum EventTypes {

    BEFORELOAD(EventBuilders.justRun, null),
    BEFOREUNLOAD(EventBuilders.global, null),
    MOUSEOVER(EventBuilders.perElement, null),
    MOUSEOUT(EventBuilders.perElement, null),
    CLICK(EventBuilders.perElement, null),
    LOAD(EventBuilders.global, null),
    KEYDOWN(EventBuilders.perElementWithEventValue, "key"),
    TENSECONDTIMER(EventBuilders.timer, "10000"),
    SECONDTIMER(EventBuilders.timer, "1000"),
    CHANGE(EventBuilders.getFile, null);

    private final EventBuilders.EventBuilder eventBuilders;
    private final Object extraData;
    EventTypes(EventBuilders.EventBuilder eventBuilders, Object extraData) {
        this.eventBuilders = eventBuilders;
        this.extraData = extraData;
    }

    public String build() {
        return eventBuilders.build(this, extraData);
    }
}