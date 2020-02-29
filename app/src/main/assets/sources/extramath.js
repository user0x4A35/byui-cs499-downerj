let Math2 = {
    factorial: function(num) {
        let result = 1;
        for (let k = 1; k < num; k++) {
            result *= k;
        }
        return result;
    },

    product: function(/* arguments */) {
        let result = 1;
        for (let argument of arguments) {
            if (isNaN(argument)) {
                throw `Argument "${argument}" is not a number`;
            }
            result *= argument;
        }
        return result;
    },

    sum: function(/* arguments */) {
        let result = 0;
        for (let argument of arguments) {
            if (isNaN(argument)) {
                throw `Argument "${argument}" is not a number`;
            }
            result += argument;
        }
        return result;
    },

    sumRange: function(arg1, arg2 = null) {
        if ((arg1 === null) || (arg1 === undefined)) {
            throw 'Argument 1 is null or undefined';
        }
        if ((arg2 === null) && (arg2 < arg1)) {
            throw 'Argument 2 is less than argument 1';
        }
        if (isNaN(arg1)) {
            throw 'Argument 1 is not a number';
        }
        if (isNaN(arg2)) {
            throw 'Argument 2 is not a number';
        }
        let minVal;
        let maxVal;
        if (arg2 === null) {
            minVal = 0;
            maxVal = arg1;
        } else {
            minVal = arg1;
            maxVal = arg2;
        }
        let result = 0;
        for (let k = minVal; k <= maxVal; k++) {
            result += k;
        }
        return result;
    },

    degToRad: function(degrees) {
        return Math.PI * degrees / 180.0;
    },

    radToDeg: function(radians) {
        return radians * 180.0 / Math.PI;
    },
};