package io.github.olaaa.redissoncachelab.scenario;

/* Общий вход для сброса локальных кэшей между тестами и через POST /lab/reset. */
public interface LabCache {

    void invalidateAll();
}
