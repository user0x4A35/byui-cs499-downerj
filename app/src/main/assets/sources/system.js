function sleep(milliseconds) {
    var start = new Date().valueOf();
    var elapsed;
    do {
        var current = new Date().valueOf();
        elapsed = current - start;
    } while (elapsed < milliseconds);
}