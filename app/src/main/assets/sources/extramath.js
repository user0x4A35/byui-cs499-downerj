function factorial(num) {
    let result = 1;
    for (let k = 1; k < num; k++) {
        result *= k;
    }
    return result;
}