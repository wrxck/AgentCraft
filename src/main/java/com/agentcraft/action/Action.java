package com.agentcraft.action;

import com.agentcraft.agent.AIAgent;

public abstract class Action {

    protected final AIAgent agent;

    public Action(AIAgent agent) {
        this.agent = agent;
    }

    public abstract ActionResult tick();

    public void onStart() {}

    public void onComplete() {}

    public void onFail() {}
}
