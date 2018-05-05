# now it works only with one variable

a = 1

if a > 10 or a < 20:
    pass

if a > 0 and a < 0:
    pass

if (a > 10 or a < 5) or (a >= 5 and a <= 10):
    pass

if a == 10 or a != 10:
    pass

if (a > 1 and a < 5) and a > 10:
    pass

if a > 10 or 20 > a:
    pass

if -1 > a or 5 < a or a < 11 and a > -3:
  pass

if a > 10 or 3 < 4:
    pass

if a != 6 and -1 != -1:
    pass

b = 1

# sometimes ot works with muliple variables...
if a > 5 or a < 10 or b == 1:
    pass

# but at most not
if b == 1 or a > 5 or a < 10:
    pass