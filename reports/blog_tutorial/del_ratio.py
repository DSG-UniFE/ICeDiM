#!/usr/bin/python

'''
A simple to compute the delivery ratio of the messages (with 95% CI) under
various scenarios.
'''
__author__ = "Barun Kumar Saha"
__copyright__ = "Copyright 2013, Barun Kumar Saha"
__license__ = "MIT"
__version__ = "1.0"

import csv
import _gen_stats as gs
import math


# Routers used
routers = ('EpidemicRouter', 'SprayAndWaitRouter',)
areas = ('500,500', '1000,1000', '1500,1500',)
rng_max = 5


for router in routers:
	print '# Router', router
	for area in areas:
		del_ratio = []
		for i in xrange(1, rng_max+1):
			fname = 'scenario_%s_area-%s_rng-%d_MessageStatsReport.txt' % (router, area, i,)
			del_ratio.append(gs.get_stat(fname))
			
			# Average delivery ratio
			avg = gs.get_average(del_ratio)
			sd = gs.get_std_dev(del_ratio)
			ci = gs.confidence_interval_mean(rng_max, sd)
			
			print '%s %.2f %.4f' % (area, avg, ci,)
			
			# Any two data sets for Gnuplot must be separated by exactly 2 blank lines
			print '\n'